package com.microinvent.catalog.repository;

import com.microinvent.catalog.domain.Customer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository extends JpaRepository<Customer, Integer>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndDeletedFalse(Integer id);

    /** Mã là UNIQUE ở DB kể cả khách đã xóa mềm. */
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByTaxCodeAndDeletedFalse(String taxCode);

    boolean existsByTaxCodeAndDeletedFalseAndIdNot(String taxCode, Integer id);

    /** Cho API nội bộ: lấy cả bản ghi đã xóa mềm. */
    List<Customer> findByIdIn(Collection<Integer> ids);
}
