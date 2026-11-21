package com.microinvent.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.catalog.config.CatalogProperties;
import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.domain.ProductGroup;
import com.microinvent.catalog.repository.ProductGroupRepository;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.catalog.web.dto.ProductImportResult;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProductExcelServiceTest {

    private final CatalogAudit audit = org.mockito.Mockito.mock(CatalogAudit.class);

    private ProductRepository products;
    private ProductGroupRepository groups;
    private ProductExcelService service;
    private ProductGroup router;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        groups = mock(ProductGroupRepository.class);
        service = new ProductExcelService(products, groups,
                Validation.buildDefaultValidatorFactory().getValidator(),
                new CatalogProperties("k", null, null, 3, null), audit);
        router = new ProductGroup();
        router.setId(1);
        router.setCode("CISCO_ROUTER");
        router.setName("Router");
        when(groups.findByDeletedFalse()).thenReturn(List.of(router));
    }

    private static MockMultipartFile xlsx(Object[]... rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet();
            Row h = s.createRow(0);
            for (int i = 0; i < ProductExcelService.IMPORT_HEADERS.length; i++) {
                h.createCell(i).setCellValue(ProductExcelService.IMPORT_HEADERS[i]);
            }
            int r = 1;
            for (Object[] values : rows) {
                Row row = s.createRow(r++);
                for (int c = 0; c < values.length; c++) {
                    if (values[c] instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (values[c] != null) {
                        row.createCell(c).setCellValue(values[c].toString());
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "sp.xlsx", null, out.toByteArray());
        }
    }

    @Test
    void importCreatesNewAndUpdatesExisting() throws Exception {
        Product existing = new Product();
        existing.setId(9);
        existing.setCode("ISR-4331");
        when(products.findByCodeIn(anyCollection())).thenReturn(List.of(existing));

        ProductImportResult res = service.importFile(xlsx(
                new Object[] {"isr-4331", "Router ISR 4331", "cisco_router", "cái", 1000, 1500, null, 2, "Có", 12},
                new Object[] {},
                new Object[] {"SFP-1G", "Module SFP", "CISCO_ROUTER", "cái", null, 300.5, null, null, "Không"}));

        assertThat(res).isEqualTo(new ProductImportResult(2, 1, 1));
        assertThat(existing.getName()).isEqualTo("Router ISR 4331");
        assertThat(existing.isHasSerial()).isTrue();
        assertThat(existing.getDefaultPriceOut()).isEqualByComparingTo("1500");
        assertThat(existing.getWarrantyMonths()).isEqualTo(12);
    }

    @Test
    @SuppressWarnings("unchecked")
    void importSavesNothingWhenAnyRowIsInvalid() throws Exception {
        when(products.findByCodeIn(anyCollection())).thenReturn(List.of());
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.importFile(xlsx(
                        new Object[] {"A1", "Tên", "CISCO_ROUTER", "cái", "abc"},
                        new Object[] {"A1", "", "KHONG_CO", "cái", null, null, null, null, "có lẽ"},
                        new Object[] {"A2", "Tên", null, "cái", -5})));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        List<String> errors = (List<String>) ((Map<String, Object>) ex.getDetails()).get("errors");
        assertThat(errors).anyMatch(e -> e.startsWith("Dòng 2: Giá nhập"))
                .anyMatch(e -> e.equals("Dòng 3: Mã A1 bị lặp trong file"))
                .anyMatch(e -> e.startsWith("Dòng 3: Tên SP"))
                .anyMatch(e -> e.equals("Dòng 3: Không có nhóm sản phẩm mã KHONG_CO"))
                .anyMatch(e -> e.startsWith("Dòng 3: Có serial"))
                .anyMatch(e -> e.equals("Dòng 4: Thiếu mã nhóm"))
                .anyMatch(e -> e.startsWith("Dòng 4: Giá nhập: Giá không được âm"));
        verify(products, never()).saveAll(anyCollection());
    }

    @Test
    void importLimitsRowCount() throws Exception {
        Object[] row = {"A", "Tên", "CISCO_ROUTER", "cái"};
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.importFile(xlsx(row, row, row, row)));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.FILE_INVALID);
        BusinessException notExcel = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.importFile(new MockMultipartFile("file", "a.xlsx", null, "abc".getBytes())));
        assertThat(notExcel.getCode()).isEqualTo(ErrorCode.FILE_INVALID);
    }

    @Test
    void exportWritesRowsReadableByImport() throws Exception {
        Product p = new Product();
        p.setCode("ISR");
        p.setName("Router");
        p.setGroup(router);
        p.setUnit("cái");
        p.setDefaultPriceOut(new BigDecimal("1234567"));
        p.setHasSerial(true);

        byte[] file = service.write(List.of(p));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Row row = wb.getSheetAt(0).getRow(1);
            assertThat(ProductExcelService.text(row, 0)).isEqualTo("ISR");
            assertThat(ProductExcelService.text(row, 2)).isEqualTo("CISCO_ROUTER");
            assertThat(ProductExcelService.text(row, 5)).isEqualTo("1234567");
            assertThat(ProductExcelService.text(row, 8)).isEqualTo("Có");
            assertThat(ProductExcelService.text(row, 11)).isEqualTo("Router");
        }
    }
}
