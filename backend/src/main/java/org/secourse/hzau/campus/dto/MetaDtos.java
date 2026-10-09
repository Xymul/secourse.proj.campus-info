package org.secourse.hzau.campus.dto;

import java.util.List;

/**
 * 基础数据（学校 / 分类 / 地点）视图对象。
 */
public final class MetaDtos {

    private MetaDtos() {
    }

    public record SchoolView(
            Long schoolId,
            String code,
            String name,
            String city,
            Double centerLatitude,
            Double centerLongitude,
            Integer defaultZoom,
            String status,
            /** 是否演示/测试数据校区 */
            Boolean isTestData,
            Integer sortOrder
    ) {
    }

    public record CategoryView(
            Long categoryId,
            String code,
            String name,
            String description,
            String icon,
            String color,
            Integer baseValidDays,
            Integer sortOrder,
            String status,
            Long postCount
    ) {
    }

    public record CategoryRequest(
            String code,
            String name,
            String description,
            String icon,
            String color,
            Integer baseValidDays,
            Integer sortOrder,
            String status
    ) {
    }

    public record LocationView(
            Long locationId,
            Long schoolId,
            String schoolName,
            String name,
            String area,
            Double latitude,
            Double longitude,
            String description,
            String status,
            Long postCount,
            Double distanceKm
    ) {
    }

    public record LocationRequest(
            Long schoolId,
            String name,
            String area,
            Double latitude,
            Double longitude,
            String description,
            String status
    ) {
    }

    public record LocationAggregate(
            Long locationId,
            String locationName,
            Double latitude,
            Double longitude,
            int count,
            List<PostDtos.Summary> posts
    ) {
    }
}
