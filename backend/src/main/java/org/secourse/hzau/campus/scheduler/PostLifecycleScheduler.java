package org.secourse.hzau.campus.scheduler;

import org.secourse.hzau.campus.config.AppProperties;
import org.secourse.hzau.campus.entity.enums.TaskStatus;
import org.secourse.hzau.campus.repository.AuthTokenRepository;
import org.secourse.hzau.campus.service.GovernanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 生命周期定时任务（F5）：
 *
 * <ol>
 *   <li>每天定时把到达有效期的信息置为「已过期」，实现信息治理闭环；</li>
 *   <li>清理过期登录令牌，避免表无限增长。</li>
 * </ol>
 *
 * <p>每个任务都会先写入任务记录（独立事务），执行完成或失败后回写结果，
 * 因此后台「任务记录」中可以完整看到每次执行的时间、影响行数与结果。</p>
 */
@Component
public class PostLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostLifecycleScheduler.class);

    private final GovernanceService governanceService;
    private final AuthTokenRepository authTokenRepository;
    private final AppProperties properties;

    public PostLifecycleScheduler(GovernanceService governanceService,
                                  AuthTokenRepository authTokenRepository,
                                  AppProperties properties) {
        this.governanceService = governanceService;
        this.authTokenRepository = authTokenRepository;
        this.properties = properties;
    }

    /** 每日过期治理（cron 可通过 campus.governance.expire-cron 配置） */
    @Scheduled(cron = "${campus.governance.expire-cron:0 10 3 * * *}")
    public void expirePostsTask() {
        log.info("【定时任务】开始执行过期信息治理");
        Long taskId = governanceService.startTask("过期信息自动治理", "POST_EXPIRE");
        try {
            int affected = governanceService.expirePosts();
            governanceService.finishTask(taskId, TaskStatus.SUCCESS, affected,
                    "已自动过期 " + affected + " 条信息");
            log.info("【定时任务】过期治理完成，处理 {} 条信息", affected);
        } catch (Exception ex) {
            governanceService.finishTask(taskId, TaskStatus.FAILED, 0, "执行失败：" + ex.getMessage());
            log.error("【定时任务】过期治理失败", ex);
        }
    }

    /** 每小时清理过期令牌 */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    public void cleanExpiredTokensTask() {
        Long taskId = governanceService.startTask("过期登录令牌清理", "TOKEN_CLEAN");
        try {
            int deleted = authTokenRepository.deleteExpired(LocalDateTime.now());
            governanceService.finishTask(taskId, TaskStatus.SUCCESS, deleted, "清理过期令牌 " + deleted + " 个");
        } catch (Exception ex) {
            governanceService.finishTask(taskId, TaskStatus.FAILED, 0, "执行失败：" + ex.getMessage());
            log.error("【定时任务】令牌清理失败", ex);
        }
    }

    /** 供运维排查：当前生效的过期治理表达式 */
    public String currentExpireCron() {
        return properties.getGovernance().getExpireCron();
    }
}
