package com.microinvent.catalog.service;

import static com.microinvent.catalog.service.SupplierServiceTest.code;
import static com.microinvent.catalog.service.SupplierServiceTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microinvent.catalog.domain.Customer;
import com.microinvent.catalog.repository.CustomerRepository;
import com.microinvent.catalog.security.FieldEncryptor;
import com.microinvent.catalog.security.FieldEncryptorTest;
import com.microinvent.catalog.web.dto.CustomerRequest;
import com.microinvent.catalog.web.dto.CustomerResponse;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {

    private final CatalogAudit audit = org.mockito.Mockito.mock(CatalogAudit.class);

    private CustomerRepository repo;
    private FieldEncryptor enc;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        repo = mock(CustomerRepository.class);
        enc = new FieldEncryptor(FieldEncryptorTest.KEY);
        service = new CustomerService(repo, enc, audit);
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(12);
            return c;
        });
        login("SALES");
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private static CustomerRequest req(Customer.Type type, String taxCode, String idCard) {
        return new CustomerRequest(null, "Nguyễn Văn A", type, taxCode, idCard, null, "0901234567", null, null);
    }

    @Test
    void createDefaultsToRetailAndMasksIdCardForSales() {
        CustomerResponse res = service.create(req(null, null, "001 099 012 345"));

        assertThat(res.code()).isEqualTo("KH00012");
        assertThat(res.customerType()).isEqualTo(Customer.Type.RETAIL);
        assertThat(res.idCardNo()).isEqualTo("********2345");
        assertThat(res.idCardMasked()).isTrue();
    }

    @Test
    void businessNeedsTaxCodeAndTaxCodeIsUnique() {
        assertThat(code(() -> service.create(req(Customer.Type.BUSINESS, null, null))))
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        when(repo.existsByTaxCodeAndDeletedFalse("0101234567")).thenReturn(true);
        assertThat(code(() -> service.create(req(Customer.Type.BUSINESS, "0101234567", null))))
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(code(() -> service.create(req(null, null, "12345")))).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void accountantSeesFullIdCardInDetail() {
        Customer c = new Customer();
        c.setId(5);
        c.setIdCardNo(enc.encrypt("001099012345"));
        when(repo.findByIdAndDeletedFalse(5)).thenReturn(Optional.of(c));
        login("ACCOUNTANT");
        assertThat(service.get(5).idCardNo()).isEqualTo("001099012345");
    }
}
