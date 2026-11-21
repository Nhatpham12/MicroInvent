package com.microinvent.catalog.repository;

import com.microinvent.catalog.domain.Supplier;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierRepository extends JpaRepository<Supplier, Integer>, JpaSpecificationExecutor<Supplier> {

    Optional<Supplier> findByIdAndDeletedFalse(Integer id);

    /** Mã và MST là UNIQUE ở DB kể cả NCC đã xóa mềm. */
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByTaxCode(String taxCode);

    boolean existsByTaxCodeAndIdNot(String taxCode, Integer id);

    /** Cho API nội bộ: lấy cả bản ghi đã xóa mềm (chứng từ cũ vẫn cần tên). */
    List<Supplier> findByIdIn(Collection<Integer> ids);
}
