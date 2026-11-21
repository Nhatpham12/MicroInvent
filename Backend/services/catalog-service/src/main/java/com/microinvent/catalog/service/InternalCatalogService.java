package com.microinvent.catalog.service;

import com.microinvent.catalog.domain.Customer;
import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.domain.Supplier;
import com.microinvent.catalog.repository.CustomerRepository;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.catalog.repository.SupplierRepository;
import com.microinvent.catalog.web.internal.InternalBatchDtos.CustomerSnapshot;
import com.microinvent.catalog.web.internal.InternalBatchDtos.PartnerBatchResponse;
import com.microinvent.catalog.web.internal.InternalBatchDtos.ProductBatchResponse;
import com.microinvent.catalog.web.internal.InternalBatchDtos.ProductSnapshot;
import com.microinvent.catalog.web.internal.InternalBatchDtos.SupplierSnapshot;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API nội bộ: các service chứng từ lấy snapshot sản phẩm / NCC / khách theo danh sách id (1 query mỗi loại). */
@Service
@Transactional(readOnly = true)
public class InternalCatalogService {

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;

    public InternalCatalogService(ProductRepository products, SupplierRepository suppliers,
            CustomerRepository customers) {
        this.products = products;
        this.suppliers = suppliers;
        this.customers = customers;
    }

    public ProductBatchResponse products(Collection<Integer> ids) {
        Set<Integer> wanted = distinct(ids);
        List<ProductSnapshot> found = wanted.isEmpty() ? List.of()
                : products.findByIdIn(wanted).stream().sorted(Comparator.comparing(Product::getId))
                        .map(InternalCatalogService::snapshot).toList();
        return new ProductBatchResponse(found, notFound(wanted, found, ProductSnapshot::id));
    }

    public PartnerBatchResponse partners(Collection<Integer> supplierIds, Collection<Integer> customerIds) {
        Set<Integer> s = distinct(supplierIds);
        Set<Integer> c = distinct(customerIds);
        List<SupplierSnapshot> sup = s.isEmpty() ? List.of()
                : suppliers.findByIdIn(s).stream().sorted(Comparator.comparing(Supplier::getId))
                        .map(InternalCatalogService::snapshot).toList();
        List<CustomerSnapshot> cus = c.isEmpty() ? List.of()
                : customers.findByIdIn(c).stream().sorted(Comparator.comparing(Customer::getId))
                        .map(InternalCatalogService::snapshot).toList();
        return new PartnerBatchResponse(sup, cus, notFound(s, sup, SupplierSnapshot::id),
                notFound(c, cus, CustomerSnapshot::id));
    }

    private static Set<Integer> distinct(Collection<Integer> ids) {
        return ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static <T> List<Integer> notFound(Set<Integer> wanted, List<T> found, Function<T, Integer> id) {
        Set<Integer> got = found.stream().map(id).collect(Collectors.toSet());
        return wanted.stream().filter(i -> !got.contains(i)).toList();
    }

    static ProductSnapshot snapshot(Product p) {
        return new ProductSnapshot(p.getId(), p.getCode(), p.getName(), p.getUnit(), p.getGroup().getId(),
                p.getGroup().getCode(), p.getGroup().getName(), p.getDefaultPriceIn(), p.getDefaultPriceOut(),
                p.getDefaultRentPricePerDay(), p.getMinStock(), p.isHasSerial(), p.getWarrantyMonths(),
                p.isDeleted());
    }

    static SupplierSnapshot snapshot(Supplier s) {
        return new SupplierSnapshot(s.getId(), s.getCode(), s.getName(), s.getTaxCode(), s.getAddress(),
                s.getPhoneFax(), s.getEmail(), s.isDeleted());
    }

    static CustomerSnapshot snapshot(Customer c) {
        return new CustomerSnapshot(c.getId(), c.getCode(), c.getName(), c.getCustomerType().name(),
                c.getTaxCode(), c.getAddress(), c.getPhone(), c.getEmail(), c.isDeleted());
    }
}
