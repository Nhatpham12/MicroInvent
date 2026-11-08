package com.microinvent.inventory.repository;

import com.microinvent.inventory.domain.ProductWarehouseSetting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductWarehouseSettingRepository
        extends JpaRepository<ProductWarehouseSetting, ProductWarehouseSetting.Key> {

    List<ProductWarehouseSetting> findByWarehouseIdOrderByProductId(Integer warehouseId);
}
