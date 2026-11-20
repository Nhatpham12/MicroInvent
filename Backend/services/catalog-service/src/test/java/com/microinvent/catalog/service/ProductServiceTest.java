package com.microinvent.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.domain.ProductGroup;
import com.microinvent.catalog.repository.ProductGroupRepository;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.catalog.web.dto.ProductRequest;
import com.microinvent.catalog.web.dto.ProductResponse;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ProductServiceTest {

    private ProductRepository products;
    private ProductGroupRepository groups;
    private ProductService service;
    private ProductGroup router;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        groups = mock(ProductGroupRepository.class);
        service = new ProductService(products, groups);
        router = new ProductGroup();
        router.setId(1);
        router.setCode("CISCO_ROUTER");
        router.setName("Router Cisco");
        when(groups.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(router));
        when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProductRequest req(String code, Integer groupId) {
        return new ProductRequest(code, "  Router ISR 4331 ", groupId, "cái", new BigDecimal("1000"),
                new BigDecimal("1500"), null, 2, true, 12, " ");
    }

    private static ErrorCode code(Runnable r) {
        try {
            r.run();
        } catch (BusinessException ex) {
            return ex.getCode();
        }
        throw new AssertionError("phải ném BusinessException");
    }

    @Test
    void createNormalizesCodeAndTrimsFields() {
        ProductResponse res = service.create(req("isr-4331", 1));

        assertThat(res.code()).isEqualTo("ISR-4331");
        assertThat(res.name()).isEqualTo("Router ISR 4331");
        assertThat(res.groupCode()).isEqualTo("CISCO_ROUTER");
        assertThat(res.hasSerial()).isTrue();
        assertThat(res.description()).isNull();
    }

    @Test
    void createRejectsDuplicateCodeAndMissingGroup() {
        when(products.existsByCodeIgnoreCase("ISR-4331")).thenReturn(true);
        assertThat(code(() -> service.create(req("isr-4331", 1)))).isEqualTo(ErrorCode.CONFLICT);

        assertThat(code(() -> service.create(req("NEW-1", 99)))).isEqualTo(ErrorCode.BAD_REQUEST);
        verify(products, never()).save(any());
    }

    @Test
    void updateKeepsCodeAndDeleteIsSoft() {
        Product p = new Product();
        p.setId(10);
        p.setCode("ISR-4331");
        p.setGroup(router);
        when(products.findByIdAndDeletedFalse(10)).thenReturn(Optional.of(p));

        ProductResponse res = service.update(10, req("KHAC", 1));
        assertThat(res.code()).isEqualTo("ISR-4331");

        service.delete(10);
        assertThat(p.isDeleted()).isTrue();
        assertThat(code(() -> service.get(11))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void priceRangeMustBeValid() {
        ProductService.Filter f = new ProductService.Filter(null, null, new BigDecimal("10"), BigDecimal.ONE, null);
        assertThat(code(() -> service.search(f, Pageable.unpaged()))).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void escapeLikeEscapesWildcards() {
        assertThat(ProductService.escapeLike("50%_off!")).isEqualTo("50!%!_off!!");
    }
}
