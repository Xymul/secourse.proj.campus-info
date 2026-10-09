package org.secourse.hzau.campus.service;

import jakarta.persistence.criteria.Predicate;
import org.secourse.hzau.campus.common.BizException;
import org.secourse.hzau.campus.common.PageResult;
import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.dto.PostDtos;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.School;
import org.secourse.hzau.campus.entity.enums.PostStatus;
import org.secourse.hzau.campus.repository.PostRepository;
import org.secourse.hzau.campus.repository.PostSpatialRepository;
import org.secourse.hzau.campus.repository.SchoolRepository;
import org.secourse.hzau.campus.search.SensitiveWordFilter;
import org.secourse.hzau.campus.search.TextUtils;
import org.secourse.hzau.campus.security.CurrentUser;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 信息节点服务：地图浏览（F1）与信息发布（F3）。
 *
 * <p>写操作统一使用 {@code @Transactional}：发布信息与操作日志处于同一事务，
 * 任何一步失败都会整体回滚，避免出现「有信息没日志」或「发布了一半」的脏数据。</p>
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostSpatialRepository postSpatialRepository;
    private final SchoolRepository schoolRepository;
    private final MetaService metaService;
    private final AuthService authService;
    private final PostMapper postMapper;
    private final OperationLogService operationLogService;
    private final AppProperties properties;

    public PostService(PostRepository postRepository,
                       PostSpatialRepository postSpatialRepository,
                       SchoolRepository schoolRepository,
                       MetaService metaService,
                       AuthService authService,
                       PostMapper postMapper,
                       OperationLogService operationLogService,
                       AppProperties properties) {
        this.postRepository = postRepository;
        this.postSpatialRepository = postSpatialRepository;
        this.schoolRepository = schoolRepository;
        this.metaService = metaService;
        this.authService = authService;
        this.postMapper = postMapper;
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    // ------------------------------------------------------------- 浏览与列表

    /** 通用列表查询：支持分类、地点、关键词、状态、作者筛选与排序 */
    @Transactional(readOnly = true)
    public PageResult<PostDtos.Summary> list(Long schoolId, Long categoryId, Long locationId, String keyword,
                                             String status, Long authorId, boolean activeOnly,
                                             int page, int size, String sort) {
        Specification<Post> spec = buildSpec(schoolId, categoryId, locationId, keyword,
                parseStatuses(status), authorId, activeOnly);
        Page<Post> result = postRepository.findAll(spec, pageRequest(page, size, sort));
        return PageResult.of(result, postMapper::toSummary);
    }

    private Specification<Post> buildSpec(Long schoolId, Long categoryId, Long locationId, String keyword,
                                          List<PostStatus> statuses, Long authorId, boolean activeOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (schoolId != null) {
                predicates.add(cb.equal(root.get("school").get("schoolId"), schoolId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("categoryId"), categoryId));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("locationId"), locationId));
            }
            if (authorId != null) {
                predicates.add(cb.equal(root.get("author").get("userId"), authorId));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (activeOnly) {
                predicates.add(cb.equal(root.get("status"), PostStatus.PUBLISHED));
                predicates.add(cb.greaterThan(root.get("expireAt"), LocalDateTime.now()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("content")), like),
                        cb.like(cb.lower(root.get("tags")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<PostStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        List<PostStatus> statuses = new ArrayList<>();
        for (String item : status.split(",")) {
            try {
                statuses.add(PostStatus.valueOf(item.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BizException("状态取值不合法：" + item);
            }
        }
        return statuses;
    }

    private Pageable pageRequest(int page, int size, String sort) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Sort order = switch (sort == null ? "latest" : sort) {
            case "hot" -> Sort.by(Sort.Direction.DESC, "hotScore").and(Sort.by(Sort.Direction.DESC, "postId"));
            case "expire" -> Sort.by(Sort.Direction.ASC, "expireAt");
            case "trust" -> Sort.by(Sort.Direction.DESC, "verifyTrueCount").and(Sort.by(Sort.Direction.DESC, "postId"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "postId"));
        };
        return PageRequest.of(safePage - 1, safeSize, order);
    }

    // ------------------------------------------------------------- 详情与发布

    @Transactional
    public PostDtos.Detail detail(Long postId) {
        Post post = requirePost(postId);
        postRepository.increaseViewCount(postId);
        post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        CurrentUser user = CurrentUserContext.optional();
        boolean owner = user != null && post.getAuthor() != null
                && post.getAuthor().getUserId().equals(user.userId());
        boolean contactVisible = user != null && (user.isAdmin() || owner);
        return postMapper.toDetail(post, contactVisible, Boolean.TRUE.equals(post.getAiAssisted()));
    }

    /** 发布信息：校验 → 有效期计算 → 敏感词检查 → 落库 → 留痕（同一事务） */
    @Transactional(rollbackFor = Exception.class)
    public PostDtos.Detail publish(PostDtos.CreateRequest request) {
        CurrentUser user = CurrentUserContext.require();
        AppUser author = authService.requireUser(user.userId());
        School school = resolveSchool(request.schoolId(), author);
        Category category = metaService.requireCategory(request.categoryId());
        CampusLocation location = request.locationId() == null ? null : metaService.requireLocation(request.locationId());
        LocalDateTime expireAt = resolveExpireAt(request.expireAt(), request.validDays(), category);

        String riskLevel = SensitiveWordFilter.riskLevel(request.title() + " " + request.content());
        boolean risky = "HIGH".equals(riskLevel);

        Post post = new Post();
        post.setSchool(school);
        post.setCategory(category);
        post.setLocation(location);
        post.setAuthor(author);
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.setLatitude(request.latitude() != null ? request.latitude()
                : (location != null ? location.getLatitude() : school.getCenterLatitude()));
        post.setLongitude(request.longitude() != null ? request.longitude()
                : (location != null ? location.getLongitude() : school.getCenterLongitude()));
        post.setContact(request.contact());
        post.setImageUrl(request.imageUrl());
        post.setTags(TextUtils.joinTags(request.tags()));
        post.setExpireAt(expireAt);
        post.setAiAssisted(Boolean.TRUE.equals(request.aiAssisted()));
        post.setStatus(properties.getGovernance().isPreAudit() || risky ? PostStatus.PENDING : PostStatus.PUBLISHED);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);

        operationLogService.recordCurrent("CREATE_POST", "POST", post.getPostId(),
                "发布信息：" + post.getTitle()
                        + (risky ? "（命中敏感词，转待审核）" : "")
                        + (Boolean.TRUE.equals(request.aiAssisted()) ? "（使用 AI 辅助）" : ""));
        return postMapper.toDetail(post, true, Boolean.TRUE.equals(post.getAiAssisted()));
    }

    @Transactional
    public PostDtos.Summary update(Long postId, PostDtos.UpdateRequest request) {
        Post post = requirePost(postId);
        requireOwnerOrAdmin(post);
        if (request.title() != null && !request.title().isBlank()) {
            post.setTitle(request.title().trim());
        }
        if (request.content() != null && !request.content().isBlank()) {
            post.setContent(request.content().trim());
        }
        if (request.categoryId() != null) {
            post.setCategory(metaService.requireCategory(request.categoryId()));
        }
        if (request.locationId() != null) {
            CampusLocation location = metaService.requireLocation(request.locationId());
            post.setLocation(location);
            if (request.latitude() == null) {
                post.setLatitude(location.getLatitude());
                post.setLongitude(location.getLongitude());
            }
        }
        if (request.latitude() != null) {
            post.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            post.setLongitude(request.longitude());
        }
        if (request.contact() != null) {
            post.setContact(request.contact());
        }
        if (request.imageUrl() != null) {
            post.setImageUrl(request.imageUrl());
        }
        if (request.tags() != null) {
            post.setTags(TextUtils.joinTags(request.tags()));
        }
        if (request.expireAt() != null) {
            post.setExpireAt(request.expireAt());
        } else if (request.validDays() != null) {
            post.setExpireAt(resolveExpireAt(null, request.validDays(), post.getCategory()));
        }
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        operationLogService.recordCurrent("UPDATE_POST", "POST", postId, "编辑信息：" + post.getTitle());
        return postMapper.toSummary(post);
    }

    /** 作者标记「已解决」（如失物已认领、二手已成交） */
    @Transactional
    public void resolve(Long postId) {
        Post post = requirePost(postId);
        requireOwnerOrAdmin(post);
        post.setStatus(PostStatus.RESOLVED);
        postRepository.save(post);
        operationLogService.recordCurrent("RESOLVE_POST", "POST", postId, "标记已解决：" + post.getTitle());
    }

    /** 用户下架自己的信息；管理员下架则为治理操作 */
    @Transactional
    public void remove(Long postId, String reason) {
        Post post = requirePost(postId);
        CurrentUser user = requireOwnerOrAdmin(post);
        post.setStatus(PostStatus.REMOVED);
        post.setAuditBy(user.userId());
        post.setAuditedAt(LocalDateTime.now());
        post.setAuditRemark(reason);
        postRepository.save(post);
        operationLogService.recordCurrent("REMOVE_POST", "POST", postId,
                (user.isAdmin() ? "管理员下架：" : "作者下架：") + post.getTitle()
                        + (reason == null || reason.isBlank() ? "" : "（" + reason + "）"));
    }

    public Post requirePost(Long postId) {
        Post post = postRepository.findDetailByPostId(postId);
        if (post == null) {
            throw BizException.notFound("信息不存在或已被删除");
        }
        return post;
    }

    private CurrentUser requireOwnerOrAdmin(Post post) {
        CurrentUser user = CurrentUserContext.require();
        boolean owner = post.getAuthor() != null && post.getAuthor().getUserId().equals(user.userId());
        if (!owner && !user.isAdmin()) {
            throw BizException.forbidden("只能操作自己发布的信息");
        }
        return user;
    }

    private School resolveSchool(Long schoolId, AppUser author) {
        if (schoolId != null) {
            return metaService.requireSchool(schoolId);
        }
        if (author.getSchool() != null) {
            return author.getSchool();
        }
        return schoolRepository.findAll().stream().findFirst()
                .orElseThrow(() -> BizException.notFound("请先初始化学校数据"));
    }

    private LocalDateTime resolveExpireAt(LocalDateTime expireAt, Integer validDays, Category category) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target;
        if (expireAt != null) {
            target = expireAt;
        } else {
            int days = validDays != null ? validDays
                    : (category == null || category.getBaseValidDays() == null ? 7 : category.getBaseValidDays());
            target = now.plusDays(Math.max(1, Math.min(days, 365)));
        }
        if (!target.isAfter(now)) {
            throw new BizException("有效期必须晚于当前时间");
        }
        return target;
    }

    // ------------------------------------------------------------- 地图与空间检索

    /**
     * 地图视野加载：仅返回当前视窗内的信息节点；同一地点信息过多时聚合为「地点聚合节点」。
     */
    @Transactional(readOnly = true)
    public List<PostDtos.MapNode> mapNodes(Long schoolId, Double minLat, Double maxLat, Double minLng, Double maxLng,
                                          Long categoryId, Integer limit) {
        int max = limit == null ? properties.getSearch().getMapMaxNodes() : Math.min(Math.max(1, limit), 2000);
        List<Long> ids = new ArrayList<>();
        if (minLat != null && maxLat != null && minLng != null && maxLng != null && schoolId != null) {
            for (Object[] row : postSpatialRepository.findIdsInBounds(schoolId, minLat, maxLat, minLng, maxLng,
                    LocalDateTime.now(), PageRequest.of(0, max))) {
                ids.add(((Number) row[0]).longValue());
            }
        } else {
            PageResult<PostDtos.Summary> page = list(schoolId, categoryId, null, null, null, null, true, 1, max, "latest");
            return page.getList().stream()
                    .filter(item -> item.latitude() != null && item.longitude() != null)
                    .map(item -> new PostDtos.MapNode(item.postId(), item.title(), item.categoryCode(),
                            item.categoryName(), item.categoryColor(), item.locationId(), item.locationName(),
                            item.latitude(), item.longitude(), item.status(), item.verifyTrueCount(),
                            item.verifyFalseCount(), item.commentCount(), item.createdAt(), 1))
                    .toList();
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Post> posts = postRepository.findAllByPostIdIn(ids).stream()
                .filter(this::isActive)
                .filter(post -> post.getLatitude() != null && post.getLongitude() != null)
                .filter(post -> categoryId == null || (post.getCategory() != null
                        && categoryId.equals(post.getCategory().getCategoryId())))
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                .toList();
        return clusterNodes(posts);
    }

    /** 同一地点聚合：数量达到阈值时折叠为一个聚合节点，减少地图标记拥挤 */
    private List<PostDtos.MapNode> clusterNodes(List<Post> posts) {
        int threshold = Math.max(2, properties.getSearch().getSameLocationClusterSize());
        Map<Long, List<Post>> byLocation = new LinkedHashMap<>();
        List<Post> withoutLocation = new ArrayList<>();
        for (Post post : posts) {
            if (post.getLocation() == null) {
                withoutLocation.add(post);
            } else {
                byLocation.computeIfAbsent(post.getLocation().getLocationId(), key -> new ArrayList<>()).add(post);
            }
        }
        List<PostDtos.MapNode> nodes = new ArrayList<>();
        for (List<Post> group : byLocation.values()) {
            if (group.size() >= threshold) {
                Post head = group.get(0);
                nodes.add(new PostDtos.MapNode(head.getPostId(),
                        head.getLocation().getName() + "（" + group.size() + " 条信息）",
                        head.getCategory() == null ? null : head.getCategory().getCode(),
                        head.getCategory() == null ? null : head.getCategory().getName(),
                        head.getCategory() == null ? null : head.getCategory().getColor(),
                        head.getLocation().getLocationId(), head.getLocation().getName(),
                        head.getLatitude(), head.getLongitude(),
                        head.getStatus() == null ? null : head.getStatus().name(),
                        head.getVerifyTrueCount(), head.getVerifyFalseCount(), head.getCommentCount(),
                        head.getCreatedAt(), group.size()));
            } else {
                group.forEach(post -> nodes.add(postMapper.toMapNode(post, 1)));
            }
        }
        withoutLocation.forEach(post -> nodes.add(postMapper.toMapNode(post, 1)));
        return nodes;
    }

    /** 周边信息（LBS）：按距离由近及远返回有效帖子 */
    @Transactional(readOnly = true)
    public List<PostDtos.Summary> nearby(Long schoolId, double lat, double lng, Double radiusKm,
                                        Long categoryId, int limit) {
        double radius = radiusKm == null ? properties.getSearch().getDefaultRadiusKm()
                : Math.max(0.1, Math.min(radiusKm, 50d));
        int max = Math.max(1, Math.min(limit, 500));
        List<Object[]> rows = postSpatialRepository.findNearbyPostIds(schoolId, lat, lng, radius,
                LocalDateTime.now(), PageRequest.of(0, max));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> distances = new LinkedHashMap<>();
        for (Object[] row : rows) {
            distances.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }
        List<PostDtos.Summary> summaries = postRepository.findAllByPostIdIn(distances.keySet()).stream()
                .filter(post -> categoryId == null || (post.getCategory() != null
                        && categoryId.equals(post.getCategory().getCategoryId())))
                .map(post -> postMapper.toSummary(post, distances.get(post.getPostId())))
                .sorted(Comparator.comparing(PostDtos.Summary::distanceKm))
                .toList();
        return summaries.size() > max ? summaries.subList(0, max) : summaries;
    }

    /** 同地点信息聚合（点击地图聚合节点后展示） */
    @Transactional(readOnly = true)
    public PostDtos.LocationCluster locationCluster(Long locationId) {
        CampusLocation location = metaService.requireLocation(locationId);
        List<Post> posts = postRepository.findAllByLocation_LocationId(locationId).stream()
                .filter(this::isActive)
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                .toList();
        return new PostDtos.LocationCluster(location.getLocationId(), location.getName(),
                location.getLatitude(), location.getLongitude(), posts.size(),
                posts.stream().map(postMapper::toSummary).toList());
    }

    /** 我发布的信息 */
    @Transactional(readOnly = true)
    public List<PostDtos.Summary> myPosts(Long userId) {
        return postRepository.findAllByAuthor_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(postMapper::toSummary)
                .toList();
    }

    /** 近 30 天有效信息的热门标签（用于搜索页推荐词） */
    @Transactional(readOnly = true)
    public Map<String, Integer> tagFrequency(Long schoolId, int limit) {
        if (schoolId == null) {
            return Map.of();
        }
        List<Post> posts = postRepository.findSearchCandidates(schoolId, LocalDateTime.now(), PageRequest.of(0, 800));
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Post post : posts) {
            if (post.getTags() == null) {
                continue;
            }
            for (String tag : post.getTags().split(",")) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    counter.merge(trimmed, 1, Integer::sum);
                }
            }
        }
        return counter.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
    }

    public boolean isActive(Post post) {
        return post.getStatus() == PostStatus.PUBLISHED
                && post.getExpireAt() != null
                && post.getExpireAt().isAfter(LocalDateTime.now());
    }




}
