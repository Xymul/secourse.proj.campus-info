package org.secourse.hzau.campus.seed;

import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.Comment;
import org.secourse.hzau.campus.entity.Post;
import org.secourse.hzau.campus.entity.Report;
import org.secourse.hzau.campus.entity.School;
import org.secourse.hzau.campus.entity.Verification;
import org.secourse.hzau.campus.entity.enums.PostStatus;
import org.secourse.hzau.campus.entity.enums.ReportStatus;
import org.secourse.hzau.campus.entity.enums.VerificationResult;
import org.secourse.hzau.campus.repository.CommentRepository;
import org.secourse.hzau.campus.repository.PostRepository;
import org.secourse.hzau.campus.repository.ReportRepository;
import org.secourse.hzau.campus.repository.VerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 模拟信息生成器：生成 1500 余条覆盖「江大 / 复旦 / 浙大」三个校区的校园信息，
 * 用于演示地图浏览、搜索、协同验证、过期治理与后台统计。
 *
 * <p>使用固定随机种子，保证每次初始化得到一致的数据分布。</p>
 */
@Component
public class MockPostGenerator {

    private static final Logger log = LoggerFactory.getLogger(MockPostGenerator.class);

    public record LocationSeed(String name, String area, double dLat, double dLng) {
    }

    /** 校园地点模板：以学校中心点叠加偏移量生成坐标 */
    public static final List<LocationSeed> LOCATION_TEMPLATES = List.of(
            new LocationSeed("图书馆", "教学区", 0.0015, -0.0008),
            new LocationSeed("第一食堂", "生活区", 0.0008, 0.0012),
            new LocationSeed("第二食堂", "生活区", -0.0006, 0.0015),
            new LocationSeed("学生公寓A区", "生活区", -0.0018, -0.0012),
            new LocationSeed("学生公寓B区", "生活区", -0.0022, 0.0006),
            new LocationSeed("田径场", "运动区", 0.0025, 0.0018),
            new LocationSeed("篮球场", "运动区", 0.0021, 0.0026),
            new LocationSeed("体育馆", "运动区", 0.0032, 0.0008),
            new LocationSeed("教学楼A座", "教学区", 0.0006, -0.0018),
            new LocationSeed("教学楼B座", "教学区", 0.0002, -0.0024),
            new LocationSeed("实验楼", "教学区", -0.0008, -0.0026),
            new LocationSeed("南大门", "出入口", -0.0032, 0.0002),
            new LocationSeed("北大门", "出入口", 0.0038, -0.0004),
            new LocationSeed("快递驿站", "生活区", -0.0012, 0.0022),
            new LocationSeed("校园超市", "生活区", -0.0004, 0.0018),
            new LocationSeed("大学生活动中心", "活动区", 0.0012, 0.0006),
            new LocationSeed("校医院", "服务区", -0.0026, -0.0018),
            new LocationSeed("湖畔步道", "休闲区", 0.0004, 0.0028)
    );

    private final PostRepository postRepository;
    private final VerificationRepository verificationRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final AppProperties properties;

    public MockPostGenerator(PostRepository postRepository,
                             VerificationRepository verificationRepository,
                             CommentRepository commentRepository,
                             ReportRepository reportRepository,
                             AppProperties properties) {
        this.postRepository = postRepository;
        this.verificationRepository = verificationRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.properties = properties;
    }

    /** 生成模拟数据：帖子 + 协同验证明细 + 评论 + 少量举报（仅写入测试数据校区） */
    public void generate(List<School> schools,
                         Map<String, Category> categories,
                         Map<Long, List<CampusLocation>> locations,
                         Map<Long, List<AppUser>> users) {
        if (postRepository.count() > 0) {
            log.info("已存在 {} 条信息数据，跳过模拟数据生成", postRepository.count());
            return;
        }
        List<School> testSchools = schools.stream()
                .filter(school -> Boolean.TRUE.equals(school.getIsTestData()))
                .toList();
        if (testSchools.isEmpty()) {
            log.info("未配置测试数据校区（school.is_test_data = true），跳过模拟信息生成");
            return;
        }
        List<AppUser> allUsers = users.values().stream().flatMap(List::stream).toList();
        if (allUsers.isEmpty() || categories.isEmpty()) {
            log.warn("缺少基础数据，跳过模拟信息生成");
            return;
        }
        Random random = new Random(properties.getSeed().getRandomSeed());
        int total = Math.max(100, properties.getSeed().getMockPostCount());
        int perSchool = Math.max(1, total / testSchools.size());

        List<Post> posts = new ArrayList<>();
        int remaining = total;
        for (int index = 0; index < testSchools.size(); index++) {
            School school = testSchools.get(index);
            int count = index == testSchools.size() - 1 ? remaining : perSchool;
            remaining -= count;
            List<Category> categoryList = new ArrayList<>(categories.values());
            List<CampusLocation> schoolLocations = locations.getOrDefault(school.getSchoolId(), List.of());
            List<AppUser> schoolUsers = users.getOrDefault(school.getSchoolId(), allUsers);
            for (int i = 0; i < count; i++) {
                posts.add(buildPost(school, categoryList, schoolLocations, schoolUsers, random));
            }
        }
        List<Post> saved = postRepository.saveAll(posts);
        log.info("已生成模拟校园信息：{} 条，平均分布到 {} 个测试数据校区（正式校区保持为空）",
                saved.size(), testSchools.size());

        int verificationCount = seedVerifications(saved, allUsers, random);
        int commentCount = seedComments(saved, allUsers, random);
        int reportCount = seedReports(saved, allUsers, random);
        log.info("已生成协同验证 {} 条、评论 {} 条、举报 {} 条", verificationCount, commentCount, reportCount);
    }

    // ------------------------------------------------------------- 单条信息构造

    private Post buildPost(School school, List<Category> categories, List<CampusLocation> locations,
                           List<AppUser> users, Random random) {
        Category category = categories.get(random.nextInt(categories.size()));
        CampusLocation location = locations.isEmpty() ? null : locations.get(random.nextInt(locations.size()));
        AppUser author = users.get(random.nextInt(users.size()));

        String item = pick(MockContent.ITEMS.getOrDefault(category.getCode(), List.of("校园信息")), random);
        String place = location == null ? school.getName() : location.getName();
        int price = 20 + random.nextInt(600);

        String title = pick(MockContent.TITLES.getOrDefault(category.getCode(), List.of("{place}{item}")), random)
                .replace("{place}", place).replace("{item}", item).replace("{price}", String.valueOf(price));
        String content = pick(MockContent.CONTENTS, random)
                .replace("{place}", place).replace("{item}", item);

        Post post = new Post();
        post.setSchool(school);
        post.setCategory(category);
        post.setLocation(location);
        post.setAuthor(author);
        post.setTitle(limit(title, 200));
        post.setContent(limit(content, 4000));
        double lat = (location != null ? location.getLatitude() : school.getCenterLatitude())
                + (random.nextDouble() - 0.5) * 0.0006;
        double lng = (location != null ? location.getLongitude() : school.getCenterLongitude())
                + (random.nextDouble() - 0.5) * 0.0006;
        post.setLatitude(round6(lat));
        post.setLongitude(round6(lng));
        post.setContact(random.nextDouble() < 0.5 ? "13" + (700000000 + random.nextInt(299999999)) : null);
        post.setTags(buildTags(category, place, item, random));
        post.setAiAssisted(random.nextDouble() < 0.4);

        LocalDateTime createdAt = randomTime(random);
        int validDays = Math.max(1, (category.getBaseValidDays() == null ? 7 : category.getBaseValidDays())
                + random.nextInt(5) - 1);
        LocalDateTime expireAt = createdAt.plusDays(validDays);
        post.setCreatedAt(createdAt);
        post.setUpdatedAt(createdAt);
        post.setExpireAt(expireAt);
        post.setStatus(resolveStatus(createdAt, expireAt, random));

        int trueCount = random.nextDouble() < 0.45 ? random.nextInt(6) : 0;
        int falseCount = random.nextDouble() < 0.22 ? random.nextInt(4) : 0;
        post.setVerifyTrueCount(trueCount);
        post.setVerifyFalseCount(falseCount);
        post.setViewCount(random.nextInt(320));
        post.setCommentCount(0);
        post.setLikeCount(random.nextInt(16));
        post.setReportCount(0);
        post.setHotScore(computeHotScore(post));
        return post;
    }

    /** 78% 的信息在最近 5 天内发布，其余时间较久（用于演示过期治理） */
    private LocalDateTime randomTime(Random random) {
        LocalDateTime now = LocalDateTime.now();
        if (random.nextDouble() < 0.78) {
            return now.minusHours(random.nextInt(5 * 24)).minusMinutes(random.nextInt(60));
        }
        return now.minusDays(6 + random.nextInt(25)).minusHours(random.nextInt(24));
    }

    private PostStatus resolveStatus(LocalDateTime createdAt, LocalDateTime expireAt, Random random) {
        if (expireAt.isBefore(LocalDateTime.now())) {
            return PostStatus.EXPIRED;
        }
        double dice = random.nextDouble();
        if (dice < 0.86) {
            return PostStatus.PUBLISHED;
        }
        if (dice < 0.93) {
            return PostStatus.PENDING;
        }
        if (dice < 0.97) {
            return PostStatus.RESOLVED;
        }
        return PostStatus.REMOVED;
    }

    private String buildTags(Category category, String place, String item, Random random) {
        List<String> tags = new ArrayList<>();
        tags.add(category.getName());
        if (place != null) {
            tags.add(place);
        }
        if (item.length() <= 8) {
            tags.add(item);
        }
        int extra = random.nextInt(2);
        for (int i = 0; i <= extra; i++) {
            tags.add(pick(MockContent.COMMON_TAGS, random));
        }
        return String.join(",", tags.stream().distinct().limit(5).toList());
    }

    private double computeHotScore(Post post) {
        double score = Math.min(1.0, post.getViewCount() / 200d) * 1.5
                + Math.min(1.0, post.getLikeCount() / 20d) * 1.2;
        int trueCount = post.getVerifyTrueCount();
        int falseCount = post.getVerifyFalseCount();
        if (trueCount + falseCount > 0) {
            double ratio = (double) trueCount / (trueCount + falseCount);
            score += ratio >= 0.5 ? 1.2 * ratio : -1.5 * (1 - ratio);
        }
        return Math.max(0d, score);
    }

    private <T> T pick(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private double round6(double value) {
        return Math.round(value * 1000000d) / 1000000d;
    }

    // ------------------------------------------------------------- 验证 / 评论 / 举报

    /**
     * 按帖子的验证计数生成明细记录：同一用户对同一帖子只生成一条，保证与唯一约束一致，
     * 并以实际生成的条数回写帖子计数，做到「计数与明细一致」。
     */
    private int seedVerifications(List<Post> posts, List<AppUser> users, Random random) {
        List<Verification> verifications = new ArrayList<>();
        List<Post> touched = new ArrayList<>();
        for (Post post : posts) {
            List<AppUser> candidates = users.stream()
                    .filter(user -> post.getAuthor() == null
                            || !user.getUserId().equals(post.getAuthor().getUserId()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (candidates.isEmpty()) {
                continue;
            }
            java.util.Collections.shuffle(candidates, random);
            int trueTarget = Math.min(post.getVerifyTrueCount(), candidates.size());
            int falseTarget = Math.min(post.getVerifyFalseCount(), Math.max(0, candidates.size() - trueTarget));
            int cursor = 0;
            for (int i = 0; i < trueTarget; i++) {
                verifications.add(buildVerification(post, candidates.get(cursor++), VerificationResult.TRUE, random));
            }
            for (int i = 0; i < falseTarget; i++) {
                verifications.add(buildVerification(post, candidates.get(cursor++), VerificationResult.FALSE, random));
            }
            if (trueTarget != post.getVerifyTrueCount() || falseTarget != post.getVerifyFalseCount()) {
                post.setVerifyTrueCount(trueTarget);
                post.setVerifyFalseCount(falseTarget);
                post.setHotScore(computeHotScore(post));
                touched.add(post);
            }
        }
        verificationRepository.saveAll(verifications);
        if (!touched.isEmpty()) {
            postRepository.saveAll(touched);
        }
        return verifications.size();
    }

    private Verification buildVerification(Post post, AppUser user, VerificationResult result, Random random) {
        Verification verification = new Verification();
        verification.setPost(post);
        verification.setUser(user);
        verification.setResult(result);
        verification.setRemark(result == VerificationResult.TRUE ? "现场确认，信息真实" : "去看了，和描述不符");
        verification.setCreatedAt(post.getCreatedAt().plusHours(1 + random.nextInt(48)));
        return verification;
    }

    private int seedComments(List<Post> posts, List<AppUser> users, Random random) {
        List<Comment> comments = new ArrayList<>();
        for (Post post : posts) {
            if (random.nextDouble() > 0.28) {
                continue;
            }
            int count = 1 + random.nextInt(3);
            String place = post.getLocation() == null ? "校园" : post.getLocation().getName();
            for (int i = 0; i < count; i++) {
                Comment comment = new Comment();
                comment.setPost(post);
                comment.setAuthor(users.get(random.nextInt(users.size())));
                comment.setContent(pick(MockContent.COMMENT_TEXTS, random).replace("{place}", place));
                comment.setCreatedAt(post.getCreatedAt().plusHours(1 + random.nextInt(72)));
                comments.add(comment);
            }
            post.setCommentCount(count);
        }
        postRepository.saveAll(posts);
        commentRepository.saveAll(comments);
        return comments.size();
    }

    private int seedReports(List<Post> posts, List<AppUser> users, Random random) {
        List<Report> reports = new ArrayList<>();
        List<Post> touched = new ArrayList<>();
        for (Post post : posts) {
            if (random.nextDouble() > 0.015) {
                continue;
            }
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                Report report = new Report();
                report.setPost(post);
                report.setReporter(users.get(random.nextInt(users.size())));
                report.setReason(pick(MockContent.REPORT_REASONS, random));
                report.setDetail("由模拟数据生成的举报记录，用于演示后台举报处理流程。");
                report.setStatus(ReportStatus.PENDING);
                report.setCreatedAt(post.getCreatedAt().plusHours(2 + random.nextInt(72)));
                reports.add(report);
            }
            post.setReportCount(count);
            touched.add(post);
        }
        if (!touched.isEmpty()) {
            postRepository.saveAll(touched);
        }
        reportRepository.saveAll(reports);
        return reports.size();
    }



    // MOCK_GENERATOR_PART_TWO
}
