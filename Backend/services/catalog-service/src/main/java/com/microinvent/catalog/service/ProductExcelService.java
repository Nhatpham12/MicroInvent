package com.microinvent.catalog.service;

import com.microinvent.catalog.config.CatalogProperties;
import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.domain.ProductGroup;
import com.microinvent.catalog.repository.ProductGroupRepository;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.catalog.web.dto.ProductImportResult;
import com.microinvent.catalog.web.dto.ProductRequest;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Xuất / nhập danh sách sản phẩm dạng .xlsx (Apache POI). File xuất dùng luôn làm mẫu nhập: 11 cột đầu là cột
 * nhập, các cột sau chỉ để xem. Nhập theo kiểu "tất cả hoặc không": một dòng lỗi là không lưu dòng nào, trả danh
 * sách lỗi theo số dòng để người dùng sửa file.
 */
@Service
public class ProductExcelService {

    static final String[] IMPORT_HEADERS = {"Mã SP*", "Tên SP*", "Mã nhóm*", "ĐVT*", "Giá nhập", "Giá bán",
        "Giá thuê/ngày", "Ngưỡng tồn", "Có serial (Có/Không)", "Bảo hành (tháng)", "Mô tả"};
    static final String[] EXTRA_HEADERS = {"Tên nhóm", "Cập nhật lúc"};
    private static final int MAX_ERRORS = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProductRepository products;
    private final ProductGroupRepository groups;
    private final Validator validator;
    private final CatalogProperties props;
    private final CatalogAudit audit;

    public ProductExcelService(ProductRepository products, ProductGroupRepository groups, Validator validator,
            CatalogProperties props, CatalogAudit audit) {
        this.products = products;
        this.groups = groups;
        this.validator = validator;
        this.props = props;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- xuất

    @Transactional(readOnly = true)
    public byte[] export(ProductService.Filter filter, Sort sort) {
        Page<Product> page = products.findAll(ProductService.spec(filter),
                PageRequest.of(0, props.maxExportRows(), sort));
        if (page.getTotalElements() > props.maxExportRows()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Có " + page.getTotalElements()
                    + " sản phẩm, vượt quá " + props.maxExportRows() + " dòng. Vui lòng lọc bớt rồi xuất lại.");
        }
        return write(page.getContent());
    }

    /** File mẫu để nhập: chỉ có dòng tiêu đề. */
    public byte[] template() {
        return write(List.of());
    }

    byte[] write(List<Product> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("San pham");
            CellStyle header = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            Row h = sheet.createRow(0);
            int col = 0;
            for (String title : IMPORT_HEADERS) {
                cell(h, col++, title).setCellStyle(header);
            }
            for (String title : EXTRA_HEADERS) {
                cell(h, col++, title).setCellStyle(header);
            }
            int r = 1;
            for (Product p : rows) {
                Row row = sheet.createRow(r++);
                cell(row, 0, p.getCode());
                cell(row, 1, p.getName());
                cell(row, 2, p.getGroup().getCode());
                cell(row, 3, p.getUnit());
                money(row, 4, p.getDefaultPriceIn(), money);
                money(row, 5, p.getDefaultPriceOut(), money);
                money(row, 6, p.getDefaultRentPricePerDay(), money);
                number(row, 7, p.getMinStock());
                cell(row, 8, p.isHasSerial() ? "Có" : "Không");
                number(row, 9, p.getWarrantyMonths());
                cell(row, 10, p.getDescription());
                cell(row, 11, p.getGroup().getName());
                cell(row, 12, p.getUpdatedAt() == null ? null : TIME.format(p.getUpdatedAt()));
            }
            int[] widths = {16, 40, 16, 8, 14, 14, 14, 11, 12, 12, 40, 24, 17};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file Excel", ex);
        }
    }

    private static Cell cell(Row row, int col, String value) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value);
        }
        return c;
    }

    private static void money(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value.doubleValue());
            c.setCellStyle(style);
        }
    }

    private static void number(Row row, int col, Integer value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }

    // ---------------------------------------------------------------- nhập

    /** Một dòng đã đọc từ file (rowNumber đánh số như Excel, bắt đầu từ 1). */
    record ParsedRow(int rowNumber, String groupCode, ProductRequest request) {
    }

    @Transactional
    public ProductImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Vui lòng chọn file .xlsx");
        }
        List<String> errors = new ArrayList<>();
        List<ParsedRow> rows;
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            rows = parse(wb.getSheetAt(0), errors);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.FILE_INVALID, "Không đọc được file, vui lòng dùng file .xlsx");
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "File không có dòng dữ liệu nào");
        }
        return save(rows, errors);
    }

    List<ParsedRow> parse(Sheet sheet, List<String> errors) {
        List<ParsedRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlank(row)) {
                continue;
            }
            if (rows.size() >= props.maxImportRows()) {
                throw new BusinessException(ErrorCode.FILE_INVALID,
                        "File tối đa " + props.maxImportRows() + " dòng dữ liệu");
            }
            int rowNumber = i + 1;
            List<String> rowErrors = new ArrayList<>();
            ProductRequest req = new ProductRequest(text(row, 0), text(row, 1), null, text(row, 3),
                    decimal(row, 4, rowErrors), decimal(row, 5, rowErrors), decimal(row, 6, rowErrors),
                    integer(row, 7, rowErrors), bool(row, 8, rowErrors), integer(row, 9, rowErrors),
                    text(row, 10));
            String groupCode = Texts.upperCode(text(row, 2));
            if (groupCode == null) {
                rowErrors.add("Thiếu mã nhóm");
            }
            rowErrors.forEach(e -> addError(errors, rowNumber, e));
            rows.add(new ParsedRow(rowNumber, groupCode, req));
        }
        return rows;
    }

    ProductImportResult save(List<ParsedRow> rows, List<String> errors) {
        Map<String, ProductGroup> groupByCode = groups.findByDeletedFalse().stream()
                .collect(Collectors.toMap(g -> g.getCode().toUpperCase(Locale.ROOT), Function.identity()));
        Set<String> codes = rows.stream().map(r -> Texts.upperCode(r.request().code()))
                .filter(c -> c != null).collect(Collectors.toSet());
        Map<String, Product> existing = codes.isEmpty() ? Map.of()
                : products.findByCodeIn(codes).stream()
                        .collect(Collectors.toMap(p -> p.getCode().toUpperCase(Locale.ROOT), Function.identity()));

        Set<String> seen = new HashSet<>();
        Map<ParsedRow, ProductGroup> resolved = new LinkedHashMap<>();
        for (ParsedRow r : rows) {
            ProductRequest req = r.request();
            for (ConstraintViolation<ProductRequest> v : validator.validate(req)) {
                if (!v.getPropertyPath().toString().equals("groupId")) {
                    addError(errors, r.rowNumber(), label(v.getPropertyPath().toString()) + ": " + v.getMessage());
                }
            }
            String code = Texts.upperCode(req.code());
            if (code != null && !seen.add(code)) {
                addError(errors, r.rowNumber(), "Mã " + code + " bị lặp trong file");
            }
            Product current = code == null ? null : existing.get(code);
            if (current != null && current.isDeleted()) {
                addError(errors, r.rowNumber(), "Mã " + code + " thuộc sản phẩm đã xóa, không dùng lại được");
            }
            ProductGroup group = r.groupCode() == null ? null : groupByCode.get(r.groupCode());
            if (r.groupCode() != null && group == null) {
                addError(errors, r.rowNumber(), "Không có nhóm sản phẩm mã " + r.groupCode());
            }
            resolved.put(r, group);
        }
        if (!errors.isEmpty()) {
            List<String> sorted = errors.stream().sorted(Comparator.comparingInt(ProductExcelService::rowOf))
                    .limit(MAX_ERRORS).toList();
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "File có " + errors.size() + " lỗi, chưa lưu dòng nào", Map.of("errors", sorted));
        }

        int created = 0;
        int updated = 0;
        List<Product> toSave = new ArrayList<>();
        for (Map.Entry<ParsedRow, ProductGroup> e : resolved.entrySet()) {
            ProductRequest req = e.getKey().request();
            String code = Texts.upperCode(req.code());
            Product p = existing.get(code);
            if (p == null) {
                p = new Product();
                p.setCode(code);
                created++;
            } else {
                updated++;
            }
            ProductService.fill(p, req, e.getValue());
            toSave.add(p);
        }
        products.saveAll(toSave);
        audit.record("IMPORT", "PRODUCT", null, null, CatalogAudit.snapshot("totalRows", rows.size(),
                "created", created, "updated", updated, "codes", toSave.stream().map(Product::getCode).toList()));
        return new ProductImportResult(rows.size(), created, updated);
    }

    private static void addError(List<String> errors, int row, String message) {
        errors.add("Dòng " + row + ": " + message);
    }

    private static int rowOf(String error) {
        try {
            return Integer.parseInt(error.substring(5, error.indexOf(':')));
        } catch (RuntimeException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static final Map<String, String> LABELS = new HashMap<>(Map.ofEntries(
            Map.entry("code", "Mã SP"), Map.entry("name", "Tên SP"), Map.entry("unit", "ĐVT"),
            Map.entry("defaultPriceIn", "Giá nhập"), Map.entry("defaultPriceOut", "Giá bán"),
            Map.entry("defaultRentPricePerDay", "Giá thuê/ngày"), Map.entry("minStock", "Ngưỡng tồn"),
            Map.entry("warrantyMonths", "Bảo hành"), Map.entry("description", "Mô tả")));

    private static String label(String field) {
        return LABELS.getOrDefault(field, field);
    }

    private static boolean isBlank(Row row) {
        for (int c = 0; c < IMPORT_HEADERS.length; c++) {
            if (text(row, c) != null) {
                return false;
            }
        }
        return true;
    }

    /** Đọc ô thành chuỗi; ô số đọc theo giá trị thật (bỏ định dạng hiển thị như #,##0). */
    static String text(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) {
            return null;
        }
        CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        String v = switch (type) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> {
                BigDecimal n = BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros();
                yield n.scale() < 0 ? n.setScale(0).toPlainString() : n.toPlainString();
            }
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> null;
        };
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static BigDecimal decimal(Row row, int col, List<String> errors) {
        String v = text(row, col);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.replace(" ", ""));
        } catch (NumberFormatException ex) {
            errors.add(IMPORT_HEADERS[col] + ": \"" + v + "\" không phải số");
            return null;
        }
    }

    private static Integer integer(Row row, int col, List<String> errors) {
        BigDecimal d = decimal(row, col, errors);
        if (d == null) {
            return null;
        }
        try {
            return d.intValueExact();
        } catch (ArithmeticException ex) {
            errors.add(IMPORT_HEADERS[col] + ": phải là số nguyên");
            return null;
        }
    }

    private static Boolean bool(Row row, int col, List<String> errors) {
        String v = text(row, col);
        if (v == null) {
            return null;
        }
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "có", "co", "x", "yes", "y", "true", "1" -> true;
            case "không", "khong", "no", "n", "false", "0" -> false;
            default -> {
                errors.add(IMPORT_HEADERS[col] + ": chỉ nhận Có hoặc Không");
                yield null;
            }
        };
    }
}
