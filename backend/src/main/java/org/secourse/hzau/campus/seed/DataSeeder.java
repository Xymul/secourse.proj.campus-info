package org.secourse.hzau.campus.seed;

import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.entity.AppUser;
import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.School;
import org.secourse.hzau.campus.entity.enums.CommonStatus;
import org.secourse.hzau.campus.entity.enums.LocationStatus;
import org.secourse.hzau.campus.entity.enums.UserRole;
import org.secourse.hzau.campus.entity.enums.UserStatus;
import org.secourse.hzau.campus.repository.AppUserRepository;
import org.secourse.hzau.campus.repository.CampusLocationRepository;
import org.secourse.hzau.campus.repository.CategoryRepository;
import org.secourse.hzau.campus.repository.SchoolRepository;
import org.secourse.hzau.campus.security.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据初始化：学校（多租户）、分类、校园地点、演示账号与模拟信息。
 *
 * <p>初始化是幂等的：仅在对应数据为空时写入，重复启动不会产生重复数据，
 * 可通过 {@code campus.seed.enabled=false} 关闭。</p>
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public record SchoolSeed(String code, String name, String city, double lat, double lng,
                             int defaultZoom, boolean testData, int sortOrder) {
    }

    public record CategorySeed(String code, String name, String description, String icon, String color, int validDays) {
    }

    /**
     * 校区配置：
     * <ul>
     *   <li>华中农业大学两个校区为「正式校区」（is_test_data = false，排序置顶），
     *       模拟测试数据不会写入这些校区；</li>
     *   <li>江南大学 / 复旦大学 / 浙江大学为「测试数据校区」（is_test_data = true，排在正式校区之后），
     *       仅用于演示与压力测试，前端会显示「测试数据」标记。</li>
     * </ul>
     */
    public static final List<SchoolSeed> SCHOOLS = List.of(
            new SchoolSeed("hzau-main", "华中农业大学（狮子山主校区）", "武汉", 30.473990, 114.348398, 16, false, 1),
            new SchoolSeed("hzau-xiangyang", "华中农业大学（襄阳校区）", "襄阳", 31.999918, 112.276105, 15, false, 2),
            new SchoolSeed("jiangnan", "江南大学（蠡湖校区）", "无锡", 31.48560, 120.26530, 16, true, 10),
            new SchoolSeed("fudan", "复旦大学（邯郸校区）", "上海", 31.29890, 121.49980, 16, true, 11),
            new SchoolSeed("zju", "浙江大学（紫金港校区）", "杭州", 30.30840, 120.08630, 16, true, 12)
    );

    public static final List<CategorySeed> CATEGORIES = List.of(
            new CategorySeed("TALK", "分享吐槽", "校园生活体验、食堂测评、日常吐槽", "chat", "#F6A6A6", 7),
            new CategorySeed("TEAM", "组队交友", "自习、运动、出行等组队与交友", "team", "#7FB3D5", 7),
            new CategorySeed("SECONDHAND", "二手交易", "闲置转让、求购与低价出清", "cart", "#F7B267", 14),
            new CategorySeed("LOST_FOUND", "失物招领", "丢失物品发布与招领认领", "search", "#9DC88D", 30),
            new CategorySeed("ACTIVITY", "校园活动", "讲座、比赛、招新等校园活动", "flag", "#B39DDB", 7),
            new CategorySeed("OTHER", "其他", "咨询求助与其他校园信息", "more", "#B0BEC5", 7)
    );

    private final SchoolRepository schoolRepository;
    private final CategoryRepository categoryRepository;
    private final CampusLocationRepository locationRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final MockPostGenerator mockPostGenerator;
    private final AppProperties properties;

    public DataSeeder(SchoolRepository schoolRepository,
                      CategoryRepository categoryRepository,
                      CampusLocationRepository locationRepository,
                      AppUserRepository appUserRepository,
                      PasswordEncoder passwordEncoder,
                      MockPostGenerator mockPostGenerator,
                      AppProperties properties) {
        this.schoolRepository = schoolRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.mockPostGenerator = mockPostGenerator;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            log.info("演示数据初始化已关闭（campus.seed.enabled=false）");
            return;
        }
        List<School> schools = seedSchools();
        Map<String, Category> categories = seedCategories();
        Map<Long, List<CampusLocation>> locations = seedLocations(schools);
        Map<Long, List<AppUser>> users = seedUsers(schools);
        mockPostGenerator.generate(schools, categories, locations, users);
    }

    /** 校区初始化：按 code 幂等写入（新增校区可在此追加，重启不重复创建） */
    private List<School> seedSchools() {
        List<School> schools = new ArrayList<>();
        int created = 0;
        for (SchoolSeed seed : SCHOOLS) {
            School school = schoolRepository.findByCode(seed.code()).orElse(null);
            if (school == null) {
                school = new School();
                school.setCode(seed.code());
                school.setStatus(CommonStatus.ENABLED);
                created++;
            }
            // 名称、城市、中心坐标、排序与测试标记每次启动都与配置对齐，便于改名与调整顺序
            school.setName(seed.name());
            school.setCity(seed.city());
            school.setCenterLatitude(seed.lat());
            school.setCenterLongitude(seed.lng());
            school.setDefaultZoom(seed.defaultZoom());
            school.setIsTestData(seed.testData());
            school.setSortOrder(seed.sortOrder());
            schools.add(schoolRepository.save(school));
        }
        // 兜底：库中若存在配置之外的校区（运维手工添加），一并纳入后续初始化范围
        for (School other : schoolRepository.findAllByOrderBySortOrderAscSchoolIdAsc()) {
            if (schools.stream().noneMatch(item -> item.getSchoolId().equals(other.getSchoolId()))) {
                schools.add(other);
            }
        }
        List<School> ordered = schools.stream()
                .sorted(Comparator.comparing(School::getSortOrder).thenComparing(School::getSchoolId))
                .toList();
        log.info("校区就绪：{} 所（本次新建 {} 所；正式校区 {} 所，测试数据校区 {} 所）",
                ordered.size(), created,
                ordered.stream().filter(item -> !Boolean.TRUE.equals(item.getIsTestData())).count(),
                ordered.stream().filter(item -> Boolean.TRUE.equals(item.getIsTestData())).count());
        return ordered;
    }

    private Map<String, Category> seedCategories() {
        List<Category> existing = categoryRepository.findAll();
        Map<String, Category> map = new LinkedHashMap<>();
        if (!existing.isEmpty()) {
            existing.forEach(category -> map.put(category.getCode(), category));
            return map;
        }
        int order = 0;
        for (CategorySeed seed : CATEGORIES) {
            Category category = new Category();
            category.setCode(seed.code());
            category.setName(seed.name());
            category.setDescription(seed.description());
            category.setIcon(seed.icon());
            category.setColor(seed.color());
            category.setBaseValidDays(seed.validDays());
            category.setSortOrder(order++);
            category.setStatus(CommonStatus.ENABLED);
            map.put(seed.code(), categoryRepository.save(category));
        }
        log.info("已初始化分类：{} 个", map.size());
        return map;
    }

    /** 为每个学校生成校园地点 */
    private Map<Long, List<CampusLocation>> seedLocations(List<School> schools) {
        Map<Long, List<CampusLocation>> result = new LinkedHashMap<>();
        for (School school : schools) {
            List<CampusLocation> existing = locationRepository.findAllBySchool_SchoolIdOrderByNameAsc(school.getSchoolId());
            if (!existing.isEmpty()) {
                result.put(school.getSchoolId(), existing);
                continue;
            }
            List<CampusLocation> created = new ArrayList<>();
            for (MockPostGenerator.LocationSeed template : MockPostGenerator.LOCATION_TEMPLATES) {
                CampusLocation location = new CampusLocation();
                location.setSchool(school);
                location.setName(template.name());
                location.setArea(template.area());
                location.setLatitude(round6(school.getCenterLatitude() + template.dLat()));
                location.setLongitude(round6(school.getCenterLongitude() + template.dLng()));
                location.setDescription(template.area() + "内的常用信息点");
                location.setStatus(LocationStatus.VERIFIED);
                created.add(location);
            }
            List<CampusLocation> saved = locationRepository.saveAll(created);
            result.put(school.getSchoolId(), saved);
        }
        log.info("已初始化校园地点：{} 个", result.values().stream().mapToInt(List::size).sum());
        return result;
    }

    /** 演示账号：管理员归属第一个正式校区；每个校区按需初始化 6 个普通用户账号（幂等） */
    private Map<Long, List<AppUser>> seedUsers(List<School> schools) {
        Map<Long, List<AppUser>> result = new LinkedHashMap<>();
        School adminSchool = schools.stream()
                .filter(school -> !Boolean.TRUE.equals(school.getIsTestData()))
                .findFirst()
                .orElse(schools.get(0));
        if (appUserRepository.findByUsername("admin").isEmpty()) {
            appUserRepository.save(buildUser("admin", "平台管理员", "admin123", adminSchool,
                    UserRole.ADMIN, "13800000000"));
            log.info("已初始化管理员账号：admin / admin123（所属校区：{}）", adminSchool.getName());
        }
        for (School school : schools) {
            List<AppUser> schoolUsers = usersOf(school);
            if (schoolUsers.isEmpty()) {
                String prefix = Boolean.TRUE.equals(school.getIsTestData()) ? "student" : "hzau";
                int start = nextUserIndex(prefix);
                List<AppUser> created = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    int index = start + i;
                    created.add(buildUser(prefix + index, "校园用户" + index, "123456", school,
                            UserRole.USER, "139000000" + String.format("%02d", index)));
                }
                appUserRepository.saveAll(created);
                schoolUsers = created;
                log.info("已为「{}」初始化普通用户账号：{}{} ~ {}{}（密码 123456）",
                        school.getName(), prefix, start, prefix, start + 5);
            }
            result.put(school.getSchoolId(), schoolUsers);
        }
        return result;
    }

    /**
     * 取某校区的「普通用户」列表。
     *
     * <p>注意：这里刻意排除管理员账号——否则管理员所在校区会被误判为「已有用户」，
     * 从而不再初始化该校区的普通用户（模拟信息的作者也不会是管理员）。</p>
     */
    private List<AppUser> usersOf(School school) {
        return appUserRepository.findAll().stream()
                .filter(user -> user.getRole() != UserRole.ADMIN)
                .filter(user -> user.getSchool() != null
                        && school.getSchoolId().equals(user.getSchool().getSchoolId()))
                .toList();
    }

    /** 计算某前缀账号的下一个可用序号，避免与已有账号冲突 */
    private int nextUserIndex(String prefix) {
        int max = 0;
        for (AppUser user : appUserRepository.findAll()) {
            String username = user.getUsername();
            if (username == null || !username.startsWith(prefix)) {
                continue;
            }
            String suffix = username.substring(prefix.length());
            if (suffix.matches("\\d+")) {
                max = Math.max(max, Integer.parseInt(suffix));
            }
        }
        return max + 1;
    }

    private AppUser buildUser(String username, String nickname, String rawPassword, School school,
                              UserRole role, String contact) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setSchool(school);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setContact(contact);
        user.setCreditScore(role == UserRole.ADMIN ? 150 : 100);
        return user;
    }

    private double round6(double value) {
        return Math.round(value * 1000000d) / 1000000d;
    }

}
