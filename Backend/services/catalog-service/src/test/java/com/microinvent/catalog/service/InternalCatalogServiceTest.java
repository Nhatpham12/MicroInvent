package com.microinvent.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.domain.ProductGroup;
import com.microinvent.catalog.domain.Supplier;
import com.microinvent.catalog.repository.CustomerRepository;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.catalog.repository.SupplierRepository;
import com.microinvent.catalog.web.internal.InternalBatchDtos.PartnerBatchResponse;
import com.microinvent.catalog.web.internal.InternalBatchDtos.ProductBatchResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalCatalogServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final InternalCatalogService service = new InternalCatalogService(products, suppliers, customers);

    @Test
    void productsIncludesDeletedAndReportsMissingIds() {
        ProductGroup g = new ProductGroup();
        g.setId(1);
        g.setCode("ROUTER");
        Product p = new Product();
        p.setId(2);
        p.setCode("ISR");
        p.setGroup(g);
        p.setDeleted(true);
        when(products.findByIdIn(anyCollection())).thenReturn(List.of(p));

        ProductBatchResponse res = service.products(Arrays.asList(2, 9, 2, null));

        assertThat(res.products()).singleElement().satisfies(s -> {
            assertThat(s.code()).isEqualTo("ISR");
            assertThat(s.groupCode()).isEqualTo("ROUTER");
            assertThat(s.deleted()).isTrue();
        });
        assertThat(res.notFound()).containsExactly(9);
    }

    @Test
    void partnersSkipsQueryForEmptyLists() {
        Supplier s = new Supplier();
        s.setId(4);
        s.setName("Cisco");
        when(suppliers.findByIdIn(anyCollection())).thenReturn(List.of(s));

        PartnerBatchResponse res = service.partners(List.of(4), null);

        assertThat(res.suppliers()).extracting(x -> x.name()).containsExactly("Cisco");
        assertThat(res.customers()).isEmpty();
        assertThat(res.customerIdsNotFound()).isEmpty();
        verify(customers, never()).findByIdIn(anyCollection());
    }
}
