package org.secourse.hzau.campus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.secourse.hzau.campus.entity.enums.TaskStatus;

import java.time.LocalDateTime;

/**
 * 定时任务执行记录（如过期信息治理任务）。
 */
@Entity
@Table(name = "task_record")
@Getter
@Setter
@NoArgsConstructor
public class TaskRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.RUNNING;

    @Column(name = "affected_count", nullable = false)
    private Integer affectedCount = 0;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
