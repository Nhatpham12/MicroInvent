package com.microinvent.catalog.repository;

import com.microinvent.catalog.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"group"})
    Optional<Product> findByIdAndDeletedFalse(Integer id);

    /**
     * Như {@link #findByIdAndDeletedFalse} nhưng khóa dòng (SELECT … FOR UPDATE) – dùng khi upload ảnh để các lần
     * upload song song chạy tuần tự, lần sau đọc được image_url của lần trước và xóa đúng file cũ (BUG-07).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findForUpdateByIdAndDeletedFalse(Integer id);

    /** Mã là UNIQUE ở DB kể cả sản phẩm đã xóa mềm. */
    boolean existsByCodeIgnoreCase(String code);

    /** Nhập Excel: tìm sản phẩm (kể cả đã xóa mềm, vì mã là UNIQUE ở DB) theo danh sách mã. */
    @EntityGraph(attributePaths = {"group"})
    List<Product> findByCodeIn(Collection<String> codes);

    /** API nội bộ: lấy cả sản phẩm đã xóa mềm (chứng từ cũ vẫn cần snapshot). */
    @EntityGraph(attributePaths = {"group"})
    List<Product> findByIdIn(Collection<Integer> ids);

    /** Nạp sẵn nhóm để không phát sinh N+1 khi dựng response. */
    @Override
    @EntityGraph(attributePaths = {"group"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);
}
