package com.microinvent.catalog.repository;

import com.microinvent.catalog.domain.ProductGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductGroupRepository extends JpaRepository<ProductGroup, Integer> {

    Optional<ProductGroup> findByIdAndDeletedFalse(Integer id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Integer id);

    /** Danh sách nhóm chưa xóa kèm số sản phẩm (chưa xóa) trong nhóm. */
    @Query(value = """
            SELECT g.id AS id, g.code AS code, g.name AS name, g.description AS description,
                   (SELECT COUNT(*) FROM products p WHERE p.group_id = g.id AND p.is_deleted = FALSE) AS productCount
            FROM product_groups g
            WHERE g.is_deleted = FALSE
              AND (:keyword IS NULL OR g.name LIKE CONCAT('%', :keyword, '%') OR g.code LIKE CONCAT('%', :keyword, '%'))
            ORDER BY g.name
            """, nativeQuery = true)
    List<ProductGroupView> findAllWithProductCount(String keyword);

    @Query(value = "SELECT COUNT(*) FROM products WHERE group_id = :groupId AND is_deleted = FALSE",
            nativeQuery = true)
    long countActiveProducts(Integer groupId);

    interface ProductGroupView {
        Integer getId();

        String getCode();

        String getName();

        String getDescription();

        Long getProductCount();
    }
}
