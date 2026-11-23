package com.microinvent.sales.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.sales.client.InventoryClient;
import com.microinvent.sales.domain.ExportSlip;
import com.microinvent.sales.domain.ExportSlipItem;
import com.microinvent.sales.repository.ExportSlipRepository;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In phiếu xuất kho ra PDF (API #45): tên + địa chỉ kho xuất, khách hàng, các dòng (kèm serial, hạn bảo hành),
 * tổng tiền, chữ ký. Font Noto Sans nhúng vào file để hiện đúng tiếng Việt (resources/fonts).
 */
@Service
@Transactional(readOnly = true)
public class ExportSlipPdfService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color HEADER_BG = new Color(235, 235, 235);

    private final ExportSlipRepository slips;
    private final InventoryClient inventory;
    private final BaseFont regular;
    private final BaseFont bold;

    public ExportSlipPdfService(ExportSlipRepository slips, InventoryClient inventory) {
        this.slips = slips;
        this.inventory = inventory;
        this.regular = loadFont("fonts/NotoSans-Regular.ttf");
        this.bold = loadFont("fonts/NotoSans-Bold.ttf");
    }

    /** @return nội dung PDF và mã phiếu (đặt tên file) */
    public Pdf render(Integer id) {
        ExportSlip s = slips.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu xuất"));
        ExportSlipService.checkAccess(s.getWarehouseId());
        JsonNode warehouse = ExportSlipService.downstream(() -> inventory.warehouse(s.getWarehouseId())).path("data");
        return new Pdf(s.getCode(), write(s, warehouse));
    }

    public record Pdf(String code, byte[] content) {
    }

    byte[] write(ExportSlip s, JsonNode warehouse) {
        Font title = new Font(bold, 16);
        Font h = new Font(bold, 9);
        Font n = new Font(regular, 9);
        Font small = new Font(regular, 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(new Paragraph(text(warehouse, "name"), h));
            doc.add(new Paragraph("Địa chỉ: " + text(warehouse, "address")
                    + (warehouse.hasNonNull("phone") ? " · ĐT: " + warehouse.path("phone").asText() : ""), small));

            Paragraph t = new Paragraph("PHIẾU XUẤT KHO", title);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingBefore(12);
            doc.add(t);
            Paragraph sub = new Paragraph("Số: " + s.getCode() + "    Loại: " + typeName(s.getExportType())
                    + "    Trạng thái: " + statusName(s.getStatus()), n);
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);
            Paragraph dates = new Paragraph("Ngày lập: " + (s.getCreatedAt() == null ? "" : DATE_TIME.format(s.getCreatedAt()))
                    + (s.getExportDate() == null ? "" : "    Ngày xuất: " + DATE_TIME.format(s.getExportDate())), n);
            dates.setAlignment(Element.ALIGN_CENTER);
            dates.setSpacingAfter(10);
            doc.add(dates);

            Map<String, Object> c = s.getCustomerSnapshot();
            if (c != null) {
                doc.add(new Paragraph("Khách hàng: " + val(c, "name") + (c.get("code") == null ? "" : " (" + c.get("code") + ")"), n));
                if (c.get("address") != null) {
                    doc.add(new Paragraph("Địa chỉ: " + c.get("address"), n));
                }
                String contact = (c.get("phone") == null ? "" : "ĐT: " + c.get("phone"))
                        + (c.get("taxCode") == null ? "" : "    MST: " + c.get("taxCode"));
                if (!contact.isBlank()) {
                    doc.add(new Paragraph(contact, n));
                }
            }
            if (s.getNote() != null) {
                doc.add(new Paragraph("Ghi chú: " + s.getNote(), n));
            }

            PdfPTable table = new PdfPTable(new float[] {4, 10, 26, 7, 6, 12, 14, 21});
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            for (String head : List.of("STT", "Mã SP", "Tên sản phẩm", "ĐVT", "SL", "Đơn giá", "Thành tiền",
                    "Serial / Bảo hành")) {
                PdfPCell cell = new PdfPCell(new Phrase(head, h));
                cell.setBackgroundColor(HEADER_BG);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            int i = 1;
            for (ExportSlipItem it : s.getItems()) {
                Map<String, Object> p = it.getProductSnapshot();
                table.addCell(cell(String.valueOf(i++), n, Element.ALIGN_CENTER));
                table.addCell(cell(val(p, "code"), n, Element.ALIGN_LEFT));
                table.addCell(cell(val(p, "name"), n, Element.ALIGN_LEFT));
                table.addCell(cell(val(p, "unit"), n, Element.ALIGN_CENTER));
                table.addCell(cell(String.valueOf(it.getQty()), n, Element.ALIGN_RIGHT));
                table.addCell(cell(money(it.getUnitPrice()), n, Element.ALIGN_RIGHT));
                table.addCell(cell(money(it.getLineTotal()), n, Element.ALIGN_RIGHT));
                String serial = it.getSerials() == null ? "" : String.join(", ", it.getSerials());
                if (it.getWarrantyUntil() != null) {
                    serial += (serial.isEmpty() ? "" : "\n") + "BH đến " + DATE.format(it.getWarrantyUntil());
                } else if (it.getWarrantyMonths() != null && it.getWarrantyMonths() > 0) {
                    serial += (serial.isEmpty() ? "" : "\n") + "BH " + it.getWarrantyMonths() + " tháng";
                }
                table.addCell(cell(serial, small, Element.ALIGN_LEFT));
            }
            doc.add(table);

            PdfPTable totals = new PdfPTable(new float[] {70, 30});
            totals.setWidthPercentage(50);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(6);
            row(totals, "Cộng tiền hàng", money(s.getSubtotal()), n);
            row(totals, "Thuế GTGT (" + vatName(s) + ")", money(s.getVatAmount()), n);
            row(totals, "Tổng cộng", money(s.getTotalAmount()), h);
            if (s.getDepositAmount() != null && s.getDepositAmount().signum() > 0) {
                row(totals, "Đã đặt cọc", money(s.getDepositAmount()), n);
            }
            doc.add(totals);

            PdfPTable sign = new PdfPTable(4);
            sign.setWidthPercentage(100);
            sign.setSpacingBefore(24);
            for (String role : List.of("Người lập phiếu", "Thủ kho", "Người nhận hàng", "Kế toán")) {
                PdfPCell cell = new PdfPCell(new Phrase(role + "\n(Ký, ghi rõ họ tên)\n\n\n\n", h));
                cell.setBorder(0);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                sign.addCell(cell);
            }
            doc.add(sign);
            doc.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Không tạo được PDF phiếu xuất", ex);
        }
        return out.toByteArray();
    }

    private static PdfPCell cell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, f));
        c.setHorizontalAlignment(align);
        c.setPadding(3);
        return c;
    }

    private static void row(PdfPTable t, String label, String value, Font f) {
        PdfPCell l = new PdfPCell(new Phrase(label, f));
        PdfPCell v = new PdfPCell(new Phrase(value, f));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setBorder(0);
        v.setBorder(0);
        t.addCell(l);
        t.addCell(v);
    }

    /** 1.234.567 (định dạng Việt Nam, không phần lẻ nếu chẵn đồng). */
    static String money(BigDecimal v) {
        if (v == null) {
            return "";
        }
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.of("vi", "VN")));
        return f.format(v);
    }

    private static String vatName(ExportSlip s) {
        return s.getVatRate() == null || "NONE".equals(s.getVatRate().code()) ? "không chịu thuế"
                : s.getVatRate().code() + "%";
    }

    private static String typeName(ExportSlip.ExportType t) {
        return switch (t) {
            case SALE -> "Bán hàng";
            case DAMAGED -> "Xuất hủy / hỏng";
            case WARRANTY -> "Bảo hành";
            case RETURN_SUPPLIER -> "Trả nhà cung cấp";
            case OTHER -> "Khác";
        };
    }

    private static String statusName(ExportSlip.Status st) {
        return switch (st) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case EXPORTED -> "Đã xuất";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static String val(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.path(field).asText() : "";
    }

    private static BaseFont loadFont(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | DocumentException ex) {
            throw new UncheckedIOException("Không đọc được font " + path,
                    ex instanceof IOException io ? io : new IOException(ex));
        }
    }
}
