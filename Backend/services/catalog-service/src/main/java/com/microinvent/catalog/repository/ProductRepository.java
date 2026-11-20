package com.microinvent.catalog.repository;

import com.microinvent.catalog.domain.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"group"})
    Optional<Product> findByIdAndDeletedFalse(Integer id);

    /** Mã là UNIQUE ở DB kể cả sản phẩm đã xóa mềm. */
    boolean existsByCodeIgnoreCase(String code);

    /** Nạp sẵn nhóm để không phát sinh N+1 khi dựng response. */
    @Override
    @EntityGraph(attributePaths = {"group"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);
}
