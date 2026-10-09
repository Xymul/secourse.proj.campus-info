package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.entity.OperationLog;
import org.secourse.hzau.campus.repository.OperationLogRepository;
import org.secourse.hzau.campus.security.CurrentUser;
import org.secourse.hzau.campus.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作留痕：重要管理动作与业务动作统一写日志。
 *
 * <p>采用 {@code REQUIRED} 传播：日志与业务操作处于同一事务，业务回滚时日志一并回滚，
 * 保证「日志反映真实落库结果」。</p>
 */
@Service
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public void record(Long operatorId, String operatorName, String action, String targetType,
                       Long targetId, String detail) {
        operationLogRepository.save(new OperationLog(operatorId, operatorName, action, targetType, targetId, detail));
    }

    /** 以当前登录用户的身份记录日志（未登录时记录为系统操作） */
    @Transactional
    public void recordCurrent(String action, String targetType, Long targetId, String detail) {
        CurrentUser user = CurrentUserContext.optional();
        if (user == null) {
            record(null, "系统", action, targetType, targetId, detail);
        } else {
            record(user.userId(), user.nickname(), action, targetType, targetId, detail);
        }
    }
}
