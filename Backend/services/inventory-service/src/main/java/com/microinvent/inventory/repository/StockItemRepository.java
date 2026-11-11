package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.StockItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    /** Serial đã có trong hệ thống (ở bất kỳ kho / trạng thái nào). */
    @Query("SELECT i.serial FROM StockItem i WHERE i.serial IN :serials")
    List<String> findExistingSerials(Collection<String> serials);
}
