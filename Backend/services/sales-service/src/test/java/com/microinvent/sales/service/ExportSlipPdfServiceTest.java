package com.microinvent.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.microinvent.sales.client.InventoryClient;
import com.microinvent.sales.domain.ExportSlip;
import com.microinvent.sales.domain.ExportSlipItem;
import com.microinvent.sales.domain.VatRate;
import com.microinvent.sales.repository.ExportSlipRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExportSlipPdfServiceTest {

    @Test
    void rendersVietnameseSlipWithEmbeddedFont() throws Exception {
        ExportSlipPdfService service = new ExportSlipPdfService(mock(ExportSlipRepository.class),
                mock(InventoryClient.class));
        ExportSlip s = new ExportSlip();
        s.setCode("PXK-2026-0001");
        s.setStatus(ExportSlip.Status.EXPORTED);
        s.setCreatedAt(LocalDateTime.of(2026, 11, 23, 9, 30));
        s.setExportDate(LocalDateTime.of(2026, 11, 23, 10, 0));
        s.setVatRate(VatRate.V10);
        s.setSubtotal(new BigDecimal("15000000"));
        s.setVatAmount(new BigDecimal("1500000"));
        s.setTotalAmount(new BigDecimal("16500000"));
        s.setCustomerSnapshot(Map.of("code", "KH00001", "name", "Công ty Đông Á", "address", "Hà Nội"));
        ExportSlipItem i = new ExportSlipItem();
        i.setProductSnapshot(Map.of("code", "LAP01", "name", "Máy tính xách tay", "unit", "cái"));
        i.setQty(1);
        i.setUnitPrice(new BigDecimal("15000000"));
        i.setLineTotal(new BigDecimal("15000000"));
        i.setSerials(List.of("SN-001"));
        i.setWarrantyUntil(LocalDate.of(2027, 11, 23));
        s.getItems().add(i);

        byte[] pdf = service.write(s, new ObjectMapper().readTree(
                "{\"name\":\"Kho Hà Nội\",\"address\":\"Số 1 Đường Láng\",\"phone\":\"024123456\"}"));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("PHIẾU XUẤT KHO", "PXK-2026-0001", "Kho Hà Nội", "Công ty Đông Á",
                "Máy tính xách tay", "SN-001", "16.500.000");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("NotoSans");
    }

    @Test
    void moneyUsesVietnameseGrouping() {
        assertThat(ExportSlipPdfService.money(new BigDecimal("1234567"))).isEqualTo("1.234.567");
        assertThat(ExportSlipPdfService.money(new BigDecimal("1234.5"))).isEqualTo("1.234,5");
    }
}
