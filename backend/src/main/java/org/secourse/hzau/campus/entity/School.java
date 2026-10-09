package org.secourse.hzau.campus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.secourse.hzau.campus.entity.enums.CommonStatus;

import java.time.LocalDateTime;

/**
 * 学校（多租户维度）：不同校区的信息相互隔离。
 */
@Entity
@Table(name = "school")
@Getter
@Setter
@NoArgsConstructor
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "center_latitude", nullable = false)
    private Double centerLatitude;

    @Column(name = "center_longitude", nullable = false)
    private Double centerLongitude;

    @Column(name = "default_zoom", nullable = false)
    private Integer defaultZoom = 16;

    /** 是否为演示/测试数据校区（前端会显示「测试数据」标记，模拟信息只在这些校区生成） */
    @Column(name = "is_test_data", nullable = false)
    private Boolean isTestData = false;

    /** 排序：越小越靠前（正式校区置顶） */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommonStatus status = CommonStatus.ENABLED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
