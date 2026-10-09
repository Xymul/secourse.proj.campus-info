package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.TaskRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRecordRepository extends JpaRepository<TaskRecord, Long> {

    Page<TaskRecord> findAllByOrderByTaskIdDesc(Pageable pageable);

    List<TaskRecord> findTop20ByOrderByTaskIdDesc();
}
