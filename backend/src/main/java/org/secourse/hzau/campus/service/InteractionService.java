package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.common.BizException;
import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.dto.InteractionDtos;
import org.secourse.hzau.campus.dto.PostDtos;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.Claim;
import org.secourse.hzau.campus.entity.Comment;
import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.PostLike;
import org.secourse.hzau.campus.entity.Verification;
import org.secourse.hzau.campus.entity.enums.ClaimStatus;
import org.secourse.hzau.campus.entity.enums.CommonStatus;
import org.secourse.hzau.campus.entity.enums.PostStatus;
import org.secourse.hzau.campus.entity.enums.VerificationResult;
import org.secourse.hzau.campus.repository.ClaimRepository;
import org.secourse.hzau.campus.repository.CommentRepository;
import org.secourse.hzau.campus.repository.PostLikeRepository;
import org.secourse.hzau.campus.repository.PostRepository;
import org.secourse.hzau.campus.repository.VerificationRepository;
import org.secourse.hzau.campus.security.CurrentUser;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 互动与协同验证服务（F4）：评论、点赞、证实/证伪、失物招领认领。
 *
 * <p>并发控制要点：</p>
 * <ul>
 *   <li>{@code verification} 表对 (post_id, user_id) 建唯一约束，保证「同一用户对同一信息只能验证一次」；</li>
 *   <li>帖子使用 {@code @Version} 乐观锁，验证计数在并发下不会丢失更新；</li>
 *   <li>冲突时通过 {@link TransactionTemplate} 重试整个事务（事务边界在重试循环内，保证可重复执行）。</li>
 * </ul>
 */
@Service
public class InteractionService {

    private static final int MAX_RETRY = 3;
    private static final long VERIFY_COOLDOWN_SECONDS = 20;

    private final CommentRepository commentRepository;
    private final VerificationRepository verificationRepository;
    private final PostLikeRepository postLikeRepository;
    private final ClaimRepository claimRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final PostMapper postMapper;
    private final OperationLogService operationLogService;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;

    public InteractionService(CommentRepository commentRepository,
                              VerificationRepository verificationRepository,
                              PostLikeRepository postLikeRepository,
                              ClaimRepository claimRepository,
                              PostRepository postRepository,
                              PostService postService,
                              PostMapper postMapper,
                              OperationLogService operationLogService,
                              TransactionTemplate transactionTemplate,
                              AppProperties properties) {
        this.commentRepository = commentRepository;
        this.verificationRepository = verificationRepository;
        this.postLikeRepository = postLikeRepository;
        this.claimRepository = claimRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.postMapper = postMapper;
        this.operationLogService = operationLogService;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    // ------------------------------------------------------------- 评论与点赞

    @Transactional(readOnly = true)
    public List<InteractionDtos.CommentView> comments(Long postId) {
        return commentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAsc(postId, CommonStatus.ENABLED)
                .stream().map(postMapper::toCommentView).toList();
    }

    /** 新增评论与帖子评论数在同一事务内更新 */
    @Transactional(rollbackFor = Exception.class)
    public InteractionDtos.CommentView addComment(Long postId, String content) {
        CurrentUser user = CurrentUserContext.require();
        Post post = postService.requirePost(postId);
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(userEntity(user));
        comment.setContent(content.trim());
        commentRepository.save(comment);
        post.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1);
        postRepository.save(post);
        operationLogService.record(user.userId(), user.nickname(), "COMMENT", "POST", postId, "评论信息：" + post.getTitle());
        return postMapper.toCommentView(comment);
    }

    @Transactional(rollbackFor = Exception.class)
    public InteractionDtos.InteractionResult like(Long postId) {
        CurrentUser user = CurrentUserContext.require();
        Post post = postService.requirePost(postId);
        if (postLikeRepository.findByPost_PostIdAndUser_UserId(postId, user.userId()).isPresent()) {
            throw new BizException("您已经点过赞了");
        }
        PostLike like = new PostLike();
        like.setPost(post);
        like.setUser(userEntity(user));
        postLikeRepository.save(like);
        post.setLikeCount((post.getLikeCount() == null ? 0 : post.getLikeCount()) + 1);
        postRepository.save(post);
        return new InteractionDtos.InteractionResult(true, post.getLikeCount(), "点赞成功");
    }

    @Transactional(rollbackFor = Exception.class)
    public InteractionDtos.InteractionResult unlike(Long postId) {
        CurrentUser user = CurrentUserContext.require();
        Post post = postService.requirePost(postId);
        PostLike like = postLikeRepository.findByPost_PostIdAndUser_UserId(postId, user.userId())
                .orElseThrow(() -> new BizException("尚未点赞"));
        postLikeRepository.delete(like);
        post.setLikeCount(Math.max(0, (post.getLikeCount() == null ? 0 : post.getLikeCount()) - 1));
        postRepository.save(post);
        return new InteractionDtos.InteractionResult(false, post.getLikeCount(), "已取消点赞");
    }

    // ------------------------------------------------------------- 协同验证

    /**
     * 提交「证实 / 证伪」：同一用户对同一信息只保留一条记录，重复提交视为修改立场（状态互斥）。
     */
    public InteractionDtos.VerificationSummary verify(Long postId, InteractionDtos.VerifyRequest request) {
        CurrentUser user = CurrentUserContext.require();
        VerificationResult result = parseResult(request.result());
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                return transactionTemplate.execute(status -> doVerify(postId, user, result, request.remark()));
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                lastError = ex;
            }
        }
        throw new BizException("操作过于频繁，请稍后重试"
                + (lastError == null ? "" : "（" + lastError.getClass().getSimpleName() + "）"));
    }

    private InteractionDtos.VerificationSummary doVerify(Long postId, CurrentUser user,
                                                         VerificationResult result, String remark) {
        Post post = postService.requirePost(postId);
        if (post.getStatus() == PostStatus.REMOVED || post.getStatus() == PostStatus.REJECTED) {
            throw new BizException("该信息当前不可验证");
        }
        if (post.getAuthor() != null && post.getAuthor().getUserId().equals(user.userId())) {
            throw new BizException("不能验证自己发布的信息");
        }
        Verification existing = verificationRepository.findByPost_PostIdAndUser_UserId(postId, user.userId()).orElse(null);
        if (existing != null) {
            long seconds = Duration.between(existing.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (seconds < VERIFY_COOLDOWN_SECONDS) {
                throw new BizException("操作过于频繁，请 " + (VERIFY_COOLDOWN_SECONDS - seconds) + " 秒后再试");
            }
            decrease(post, existing.getResult());
            existing.setResult(result);
            existing.setRemark(remark);
            existing.setCreatedAt(LocalDateTime.now());
            verificationRepository.save(existing);
        } else {
            Verification verification = new Verification();
            verification.setPost(post);
            verification.setUser(userEntity(user));
            verification.setResult(result);
            verification.setRemark(remark);
            verificationRepository.save(verification);
        }
        increase(post, result);
        post.setHotScore(computeHotScore(post));
        postRepository.save(post);

        if (post.getVerifyFalseCount() >= properties.getGovernance().getFalseVerifyWarnThreshold()) {
            operationLogService.record(null, "系统", "ALERT_POST", "POST", postId,
                    "高频证伪预警：" + post.getTitle() + "（证伪 " + post.getVerifyFalseCount() + " 次）");
        }
        operationLogService.record(user.userId(), user.nickname(), "VERIFY_POST", "POST", postId,
                "协同验证：" + result.name() + (remark == null || remark.isBlank() ? "" : "（" + remark + "）"));
        return buildSummary(post, result.name());
    }

    private VerificationResult parseResult(String raw) {
        try {
            return VerificationResult.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException("验证结果不合法：" + raw);
        }
    }

    private void increase(Post post, VerificationResult result) {
        if (result == VerificationResult.TRUE) {
            post.setVerifyTrueCount((post.getVerifyTrueCount() == null ? 0 : post.getVerifyTrueCount()) + 1);
        } else if (result == VerificationResult.FALSE) {
            post.setVerifyFalseCount((post.getVerifyFalseCount() == null ? 0 : post.getVerifyFalseCount()) + 1);
        }
    }

    private void decrease(Post post, VerificationResult result) {
        if (result == VerificationResult.TRUE) {
            post.setVerifyTrueCount(Math.max(0,
                    (post.getVerifyTrueCount() == null ? 0 : post.getVerifyTrueCount()) - 1));
        } else if (result == VerificationResult.FALSE) {
            post.setVerifyFalseCount(Math.max(0,
                    (post.getVerifyFalseCount() == null ? 0 : post.getVerifyFalseCount()) - 1));
        }
    }

    @Transactional(readOnly = true)
    public InteractionDtos.VerificationSummary verificationSummary(Long postId) {
        Post post = postService.requirePost(postId);
        String myResult = null;
        Long userId = CurrentUserContext.optionalUserId();
        if (userId != null) {
            myResult = verificationRepository.findByPost_PostIdAndUser_UserId(postId, userId)
                    .map(verification -> verification.getResult().name()).orElse(null);
        }
        return buildSummary(post, myResult);
    }

    private InteractionDtos.VerificationSummary buildSummary(Post post, String myResult) {
        long trueCount = post.getVerifyTrueCount() == null ? 0 : post.getVerifyTrueCount();
        long falseCount = post.getVerifyFalseCount() == null ? 0 : post.getVerifyFalseCount();
        long unknownCount = verificationRepository.countByPost_PostIdAndResult(post.getPostId(), VerificationResult.UNKNOWN);
        int total = (int) (trueCount + falseCount + unknownCount);
        double ratio = (trueCount + falseCount) == 0 ? 0d : (double) trueCount / (trueCount + falseCount);
        int percent = (int) Math.round(ratio * 100);
        String level;
        if (total == 0) {
            level = "暂无验证";
        } else if (ratio >= 0.75) {
            level = "可信度高";
        } else if (ratio >= 0.5) {
            level = "基本可信";
        } else if (ratio >= 0.25) {
            level = "存在争议";
        } else {
            level = "可信度低";
        }
        boolean warned = falseCount >= properties.getGovernance().getFalseVerifyWarnThreshold();
        return new InteractionDtos.VerificationSummary(trueCount, falseCount, unknownCount, total,
                Math.round(ratio * 100) / 100d, percent, myResult, level, warned);
    }

    /** 热度分：互动 + 验证可信度，供排序与后台预警使用 */
    private double computeHotScore(Post post) {
        double score = Math.min(1.0, (post.getViewCount() == null ? 0 : post.getViewCount()) / 200d) * 1.5
                + Math.min(1.0, (post.getCommentCount() == null ? 0 : post.getCommentCount()) / 20d) * 1.5
                + Math.min(1.0, (post.getLikeCount() == null ? 0 : post.getLikeCount()) / 20d) * 1.2;
        int trueCount = post.getVerifyTrueCount() == null ? 0 : post.getVerifyTrueCount();
        int falseCount = post.getVerifyFalseCount() == null ? 0 : post.getVerifyFalseCount();
        if (trueCount + falseCount > 0) {
            double ratio = (double) trueCount / (trueCount + falseCount);
            score += ratio >= 0.5 ? 1.2 * ratio : -1.5 * (1 - ratio);
        }
        return score;
    }

    // ------------------------------------------------------------- 失物招领认领

    @Transactional(rollbackFor = Exception.class)
    public InteractionDtos.ClaimView claim(Long postId, InteractionDtos.ClaimRequest request) {
        CurrentUser user = CurrentUserContext.require();
        Post post = postService.requirePost(postId);
        if (post.getAuthor() != null && post.getAuthor().getUserId().equals(user.userId())) {
            throw new BizException("不能认领自己发布的信息");
        }
        if (claimRepository.existsByPost_PostIdAndClaimant_UserId(postId, user.userId())) {
            throw new BizException("您已提交过认领申请，请等待发布者确认");
        }
        Claim claim = new Claim();
        claim.setPost(post);
        claim.setClaimant(userEntity(user));
        claim.setDescription(request.description().trim());
        claim.setContact(request.contact());
        claim.setStatus(ClaimStatus.PENDING);
        claimRepository.save(claim);
        operationLogService.record(user.userId(), user.nickname(), "CLAIM_POST", "POST", postId,
                "提交认领申请：" + post.getTitle());
        return postMapper.toClaimView(claim);
    }

    /** 认领申请列表：发布者与管理员可见 */
    @Transactional(readOnly = true)
    public List<InteractionDtos.ClaimView> claims(Long postId) {
        CurrentUser user = CurrentUserContext.require();
        Post post = postService.requirePost(postId);
        boolean owner = post.getAuthor() != null && post.getAuthor().getUserId().equals(user.userId());
        if (!owner && !user.isAdmin()) {
            throw BizException.forbidden("只有发布者或管理员可以查看认领申请");
        }
        return claimRepository.findAllByPost_PostIdOrderByCreatedAtDesc(postId).stream()
                .map(postMapper::toClaimView).toList();
    }

    /** 处理认领申请：通过后信息置为「已解决」 */
    @Transactional(rollbackFor = Exception.class)
    public InteractionDtos.ClaimView handleClaim(Long claimId, InteractionDtos.ClaimHandleRequest request) {
        CurrentUser user = CurrentUserContext.require();
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> BizException.notFound("认领申请不存在"));
        Post post = postService.requirePost(claim.getPost().getPostId());
        boolean owner = post.getAuthor() != null && post.getAuthor().getUserId().equals(user.userId());
        if (!owner && !user.isAdmin()) {
            throw BizException.forbidden("只有发布者或管理员可以处理认领申请");
        }
        String action = request.action() == null ? "" : request.action().trim().toUpperCase();
        if ("APPROVED".equals(action) || "APPROVE".equals(action)) {
            claim.setStatus(ClaimStatus.APPROVED);
            if (post.getStatus() == PostStatus.PUBLISHED) {
                post.setStatus(PostStatus.RESOLVED);
                postRepository.save(post);
            }
        } else if ("REJECTED".equals(action) || "REJECT".equals(action)) {
            claim.setStatus(ClaimStatus.REJECTED);
        } else {
            throw new BizException("处理结果不合法：" + request.action());
        }
        claim.setHandledBy(user.userId());
        claim.setHandledAt(LocalDateTime.now());
        claim.setHandleRemark(request.remark());
        claimRepository.save(claim);
        operationLogService.record(user.userId(), user.nickname(), "HANDLE_CLAIM", "CLAIM", claimId,
                "处理认领申请：" + claim.getStatus().name() + "（" + post.getTitle() + "）");
        return postMapper.toClaimView(claim);
    }

    /** 我的互动汇总：我的发布、我的点赞、我的验证、我的认领 */
    @Transactional(readOnly = true)
    public InteractionDtos.MyInteractions myInteractions(Long userId) {
        List<PostDtos.Summary> posts = postService.myPosts(userId);
        List<Long> likedIds = postLikeRepository.findAllByUser_UserId(userId).stream()
                .map(like -> like.getPost().getPostId()).toList();
        List<PostDtos.Summary> liked = likedIds.isEmpty()
                ? List.of()
                : postRepository.findAllByPostIdIn(likedIds).stream().map(postMapper::toSummary).toList();
        List<InteractionDtos.VerificationRecord> verifications = verificationRepository
                .findAllByUser_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(verification -> new InteractionDtos.VerificationRecord(
                        verification.getPost().getPostId(),
                        verification.getPost().getTitle(),
                        verification.getResult().name(),
                        verification.getRemark(),
                        verification.getCreatedAt()))
                .toList();
        List<InteractionDtos.ClaimView> claims = claimRepository.findAllByClaimant_UserIdOrderByCreatedAtDesc(userId)
                .stream().map(postMapper::toClaimView).toList();
        return new InteractionDtos.MyInteractions(posts, liked, verifications, claims);
    }

    private AppUser userEntity(CurrentUser user) {
        AppUser entity = new AppUser();
        entity.setUserId(user.userId());
        return entity;
    }




}
