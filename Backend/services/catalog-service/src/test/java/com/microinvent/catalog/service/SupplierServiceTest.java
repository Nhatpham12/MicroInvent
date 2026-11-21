package com.microinvent.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.catalog.domain.Supplier;
import com.microinvent.catalog.repository.SupplierRepository;
import com.microinvent.catalog.security.FieldEncryptor;
import com.microinvent.catalog.security.FieldEncryptorTest;
import com.microinvent.catalog.web.dto.SupplierRequest;
import com.microinvent.catalog.web.dto.SupplierResponse;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplierServiceTest {

    private final CatalogAudit audit = org.mockito.Mockito.mock(CatalogAudit.class);

    private SupplierRepository repo;
    private FieldEncryptor enc;
    private SupplierService service;

    @BeforeEach
    void setUp() {
        repo = mock(SupplierRepository.class);
        enc = new FieldEncryptor(FieldEncryptorTest.KEY);
        service = new SupplierService(repo, enc, audit);
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Supplier s = inv.getArgument(0);
            s.setId(7);
            return s;
        });
        login("ACCOUNTANT");
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    static void login(String role) {
        UserContextHolder.set(new UserContext(1L, "u", Set.of(role), Set.of(1L), false, null));
    }

    static SupplierRequest req(String code, String bank) {
        return new SupplierRequest(code, " Cisco VN ", "0101234567", null, null, "a@b.vn", bank, null, null, " ");
    }

    static ErrorCode code(Runnable r) {
        try {
            r.run();
        } catch (BusinessException ex) {
            return ex.getCode();
        }
        throw new AssertionError("phải ném BusinessException");
    }

    @Test
    void createAutoGeneratesCodeAndEncryptsAccount() {
        SupplierResponse res = service.create(req(null, "0123 456 789"));

        assertThat(res.code()).isEqualTo("NCC00007");
        assertThat(res.name()).isEqualTo("Cisco VN");
        assertThat(res.note()).isNull();
        assertThat(res.bankAccountNo()).isEqualTo("0123456789");
        assertThat(res.bankAccountMasked()).isFalse();
        // Nhật ký không chứa STK (GAP-01)
        org.mockito.ArgumentCaptor<Object> after = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("CREATE"), org.mockito.ArgumentMatchers.eq("SUPPLIER"),
                org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.isNull(), after.capture());
        assertThat(after.getValue().toString()).contains("NCC00007").doesNotContain("0123456789");
    }

    @Test
    void generatedCodeSkipsCodeTakenByUser() {
        when(repo.existsByCodeIgnoreCase("NCC00007")).thenReturn(true);
        assertThat(service.create(req(null, null)).code()).isEqualTo("NCC00007-1");
    }

    @Test
    void createRejectsDuplicatesAndBadAccount() {
        when(repo.existsByCodeIgnoreCase("CISCO")).thenReturn(true);
        assertThat(code(() -> service.create(req("cisco", null)))).isEqualTo(ErrorCode.CONFLICT);
        when(repo.existsByTaxCode("0101234567")).thenReturn(true);
        assertThat(code(() -> service.create(req("NEW", null)))).isEqualTo(ErrorCode.CONFLICT);
        when(repo.existsByTaxCode(anyString())).thenReturn(false);
        assertThat(code(() -> service.create(req("NEW", "12")))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void detailMasksAccountForStorekeeperAndUpdateKeepsAccountWhenNull() {
        Supplier s = new Supplier();
        s.setId(3);
        s.setCode("NCC1");
        s.setBankAccountNo(enc.encrypt("0123456789"));
        when(repo.findByIdAndDeletedFalse(3)).thenReturn(Optional.of(s));

        login("STOREKEEPER");
        SupplierResponse view = service.get(3);
        assertThat(view.bankAccountNo()).isEqualTo("******6789");
        assertThat(view.bankAccountMasked()).isTrue();

        login("MANAGER");
        assertThat(service.update(3, req("KHAC", null)).bankAccountNo()).isEqualTo("0123456789");
        assertThat(s.getCode()).isEqualTo("NCC1");
        service.update(3, req(null, ""));
        assertThat(s.getBankAccountNo()).isNull();
    }

    @Test
    void deleteIsSoft() {
        Supplier s = new Supplier();
        when(repo.findByIdAndDeletedFalse(3)).thenReturn(Optional.of(s));
        service.delete(3);
        assertThat(s.isDeleted()).isTrue();
        assertThat(code(() -> service.get(4))).isEqualTo(ErrorCode.NOT_FOUND);
    }
}
