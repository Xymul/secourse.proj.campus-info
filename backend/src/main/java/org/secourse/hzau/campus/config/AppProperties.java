package org.secourse.hzau.campus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台可配置项：业务规则尽量配置化，便于后续扩展校区与规则（对应需求 5「灵活性」）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "campus")
public class AppProperties {

    private Seed seed = new Seed();
    private Auth auth = new Auth();
    private Search search = new Search();
    private Governance governance = new Governance();

    @Getter
    @Setter
    public static class Seed {
        /** 是否在启动时自动初始化演示数据（学校/分类/地点/用户/模拟帖子） */
        private boolean enabled = true;
        /** 模拟帖子数量，需求要求 1500 余条 */
        private int mockPostCount = 1560;
        /** 固定随机种子，保证多次初始化数据一致 */
        private long randomSeed = 20260918L;
    }

    @Getter
    @Setter
    public static class Auth {
        /** 登录令牌有效期（小时） */
        private int tokenTtlHours = 168;
        /** 是否允许自助注册 */
        private boolean allowRegister = true;
    }

    @Getter
    @Setter
    public static class Search {
        /** 默认周边搜索半径（公里） */
        private double defaultRadiusKm = 3.0;
        /** 新鲜度半衰期（天）；越大表示越不强调时效 */
        private double freshnessHalfLifeDays = 7.0;
        /** 地图视野一次最多返回的节点数 */
        private int mapMaxNodes = 600;
        /** 同一地点聚合阈值（超过该数量时在地图上聚合展示） */
        private int sameLocationClusterSize = 3;
    }

    @Getter
    @Setter
    public static class Governance {
        /** 证伪达到该数量触发后台预警 */
        private int falseVerifyWarnThreshold = 3;
        /** 举报达到该数量自动置为待审核 */
        private int reportAutoAuditThreshold = 2;
        /** 帖子审核模式：true 表示普通用户发布后需管理员审核，false 表示先发布后审核 */
        private boolean preAudit = false;
        /** 每日过期清理任务执行表达式（默认每天 03:10） */
        private String expireCron = "0 10 3 * * *";
    }
}
