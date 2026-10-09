package org.secourse.hzau.campus.repository;

import org.secourse.hzau.campus.entity.Category;
import org.secourse.hzau.campus.entity.enums.CommonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    List<Category> findAllByOrderBySortOrderAscCategoryIdAsc();

    List<Category> findAllByStatusOrderBySortOrderAscCategoryIdAsc(CommonStatus status);

    boolean existsByCode(String code);
}
