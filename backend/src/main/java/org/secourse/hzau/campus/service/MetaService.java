package org.secourse.hzau.campus.service;

import org.secourse.hzau.campus.common.BizException;
import org.secourse.hzau.campus.dto.MetaDtos;
import org.secourse.hzau.campus.entity.CampusLocation;
import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.School;
import org.secourse.hzau.campus.entity.enums.CommonStatus;
import org.secourse.hzau.campus.entity.enums.LocationStatus;
import org.secourse.hzau.campus.repository.CampusLocationRepository;
import org.secourse.hzau.campus.repository.CategoryRepository;
import org.secourse.hzau.campus.repository.PostRepository;
import org.secourse.hzau.campus.repository.SchoolRepository;
import org.secourse.hzau.campus.search.TextUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据服务：学校（多租户）、分类、校园地点。
 */
@Service
public class MetaService {

    private final SchoolRepository schoolRepository;
    private final CategoryRepository categoryRepository;
    private final CampusLocationRepository locationRepository;
    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final OperationLogService operationLogService;

    public MetaService(SchoolRepository schoolRepository,
                       CategoryRepository categoryRepository,
                       CampusLocationRepository locationRepository,
                       PostRepository postRepository,
                       PostMapper postMapper,
                       OperationLogService operationLogService) {
        this.schoolRepository = schoolRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.postRepository = postRepository;
        this.postMapper = postMapper;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public List<MetaDtos.SchoolView> listSchools() {
        return schoolRepository.findAllByStatusOrderBySortOrderAscSchoolIdAsc(CommonStatus.ENABLED).stream()
                .map(postMapper::toSchoolView)
                .toList();
    }

    @Transactional(readOnly = true)
    public School requireSchool(Long schoolId) {
        return schoolRepository.findById(schoolId)
                .orElseThrow(() -> BizException.notFound("学校不存在"));
    }

    @Transactional(readOnly = true)
    public List<MetaDtos.CategoryView> listCategories(boolean includeDisabled) {
        List<Category> categories = includeDisabled
                ? categoryRepository.findAllByOrderBySortOrderAscCategoryIdAsc()
                : categoryRepository.findAllByStatusOrderBySortOrderAscCategoryIdAsc(CommonStatus.ENABLED);
        return categories.stream()
                .map(category -> postMapper.toCategoryView(category,
                        postRepository.countByCategory_CategoryId(category.getCategoryId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Category> enabledCategories() {
        return categoryRepository.findAllByStatusOrderBySortOrderAscCategoryIdAsc(CommonStatus.ENABLED);
    }

    /** 学校地点（实体），供搜索意图识别与发布辅助使用 */
    @Transactional(readOnly = true)
    public List<CampusLocation> schoolLocations(Long schoolId) {
        if (schoolId == null) {
            return List.of();
        }
        return locationRepository.findAllBySchool_SchoolIdAndStatusOrderByNameAsc(schoolId, LocationStatus.VERIFIED);
    }

    @Transactional(readOnly = true)
    public Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> BizException.notFound("分类不存在"));
    }

    @Transactional
    public MetaDtos.CategoryView saveCategory(Long categoryId, MetaDtos.CategoryRequest request) {
        Category category = categoryId == null ? new Category() : requireCategory(categoryId);
        if (categoryId == null) {
            if (request.code() == null || request.code().isBlank()) {
                throw new BizException("分类编码不能为空");
            }
            if (categoryRepository.existsByCode(request.code())) {
                throw new BizException("分类编码已存在");
            }
            category.setCode(request.code().trim().toUpperCase());
        }
        if (request.name() != null && !request.name().isBlank()) {
            category.setName(request.name().trim());
        } else if (categoryId == null) {
            throw new BizException("分类名称不能为空");
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon());
        }
        if (request.color() != null) {
            category.setColor(request.color());
        }
        if (request.baseValidDays() != null) {
            category.setBaseValidDays(Math.max(1, request.baseValidDays()));
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            category.setStatus(parseStatus(request.status()));
        }
        categoryRepository.save(category);
        operationLogService.recordCurrent(categoryId == null ? "CREATE_CATEGORY" : "UPDATE_CATEGORY",
                "CATEGORY", category.getCategoryId(), "分类：" + category.getName());
        return postMapper.toCategoryView(category, postRepository.countByCategory_CategoryId(category.getCategoryId()));
    }

    private CommonStatus parseStatus(String status) {
        try {
            return CommonStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException("状态取值不合法：" + status);
        }
    }

    @Transactional(readOnly = true)
    public List<MetaDtos.LocationView> listLocations(Long schoolId, String keyword, String status,
                                                     Double lat, Double lng) {
        LocationStatus locationStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                locationStatus = LocationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BizException("地点状态不合法：" + status);
            }
        }
        List<CampusLocation> locations;
        if (schoolId == null) {
            locations = locationRepository.findAll();
        } else if (keyword == null || keyword.isBlank()) {
            locations = locationStatus == null
                    ? locationRepository.findAllBySchool_SchoolIdOrderByNameAsc(schoolId)
                    : locationRepository.findAllBySchool_SchoolIdAndStatusOrderByNameAsc(schoolId, locationStatus);
        } else {
            locations = locationRepository.search(schoolId, locationStatus, keyword, PageRequest.of(0, 200)).getContent();
        }
        Map<Long, Long> counts = locationPostCounts();
        List<MetaDtos.LocationView> views = new ArrayList<>();
        for (CampusLocation location : locations) {
            Double distance = (lat == null || lng == null)
                    ? null
                    : TextUtils.haversineKm(lat, lng, location.getLatitude(), location.getLongitude());
            views.add(postMapper.toLocationView(location, counts.getOrDefault(location.getLocationId(), 0L), distance));
        }
        if (lat != null && lng != null) {
            views.sort((a, b) -> Double.compare(a.distanceKm(), b.distanceKm()));
        }
        return views;
    }

    private Map<Long, Long> locationPostCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : postRepository.countGroupByLocationId()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Transactional
    public MetaDtos.LocationView saveLocation(Long locationId, MetaDtos.LocationRequest request) {
        CampusLocation location = locationId == null ? new CampusLocation() : requireLocation(locationId);
        if (request.schoolId() != null) {
            location.setSchool(requireSchool(request.schoolId()));
        } else if (location.getSchool() == null) {
            throw new BizException("请选择所属学校");
        }
        if (request.name() != null && !request.name().isBlank()) {
            location.setName(request.name().trim());
        } else if (locationId == null) {
            throw new BizException("地点名称不能为空");
        }
        location.setArea(request.area());
        if (request.latitude() != null) {
            location.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            location.setLongitude(request.longitude());
        }
        if (location.getLatitude() == null || location.getLongitude() == null) {
            throw new BizException("请在地图上选择地点坐标");
        }
        location.setDescription(request.description());
        if (request.status() != null) {
            try {
                location.setStatus(LocationStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BizException("地点状态不合法：" + request.status());
            }
        }
        location.setUpdatedAt(LocalDateTime.now());
        locationRepository.save(location);
        operationLogService.recordCurrent(locationId == null ? "CREATE_LOCATION" : "UPDATE_LOCATION",
                "LOCATION", location.getLocationId(), "地点：" + location.getName());
        return postMapper.toLocationView(location,
                postRepository.countByLocation_LocationId(location.getLocationId()), null);
    }

    @Transactional
    public CampusLocation requireLocation(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> BizException.notFound("地点不存在"));
    }

    @Transactional
    public void verifyLocation(Long locationId, boolean approved, String remark) {
        CampusLocation location = requireLocation(locationId);
        location.setStatus(approved ? LocationStatus.VERIFIED : LocationStatus.REJECTED);
        location.setUpdatedAt(LocalDateTime.now());
        locationRepository.save(location);
        operationLogService.recordCurrent("VERIFY_LOCATION", "LOCATION", locationId,
                (approved ? "核验通过：" : "核验驳回：") + location.getName()
                        + (remark == null || remark.isBlank() ? "" : "（" + remark + "）"));
    }

    @Transactional
    public void deleteLocation(Long locationId) {
        CampusLocation location = requireLocation(locationId);
        if (postRepository.countByLocation_LocationId(locationId) > 0) {
            throw new BizException("该地点下仍有信息节点，请先迁移或下架相关信息");
        }
        locationRepository.delete(location);
        operationLogService.recordCurrent("DELETE_LOCATION", "LOCATION", locationId, "删除地点：" + location.getName());
    }

}
