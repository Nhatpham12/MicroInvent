package com.microinvent.catalog.web;

import com.microinvent.catalog.service.ProductExcelService;
import com.microinvent.catalog.service.ProductImageService;
import com.microinvent.catalog.service.ProductService;
import com.microinvent.catalog.web.dto.ProductImportResult;
import com.microinvent.catalog.web.dto.ProductRequest;
import com.microinvent.catalog.web.dto.ProductResponse;
import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageRequests;
import com.microinvent.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Sản phẩm")
@RestController
@RequestMapping("/api/v1/products")
@RequireRoles
public class ProductController {

    private static final Set<String> SORTABLE = Set.of("id", "code", "name", "defaultPriceOut", "defaultPriceIn",
            "createdAt", "updatedAt");

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Sort DEFAULT_SORT = Sort.by("name").and(Sort.by("id"));

    private final ProductService service;
    private final ProductImageService images;
    private final ProductExcelService excel;

    public ProductController(ProductService service, ProductImageService images, ProductExcelService excel) {
        this.service = service;
        this.images = images;
        this.excel = excel;
    }

    @Operation(summary = "Danh sách sản phẩm: tìm theo mã/tên, lọc nhóm/giá bán/serial, sắp xếp, phân trang",
            description = "sort=name,asc | code | defaultPriceOut | defaultPriceIn | createdAt | updatedAt")
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer groupId, @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) Boolean hasSerial,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.search(
                new ProductService.Filter(keyword, groupId, minPrice, maxPrice, hasSerial),
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Chi tiết sản phẩm")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Tạo sản phẩm")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles({"ADMIN", "MANAGER", "STOREKEEPER"})
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Sửa sản phẩm (không đổi mã)")
    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER", "STOREKEEPER"})
    public ApiResponse<ProductResponse> update(@PathVariable Integer id, @Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Xóa mềm sản phẩm")
    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Upload ảnh sản phẩm (PNG/JPEG/WEBP, tối đa 2 MB), trả imageUrl mới",
            description = "multipart/form-data, field 'file'. Ảnh cũ bị xóa sau khi lưu thành công.")
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRoles({"ADMIN", "MANAGER", "STOREKEEPER"})
    public ApiResponse<Map<String, String>> uploadImage(@PathVariable Integer id,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("imageUrl", images.upload(id, file)));
    }

    @Operation(summary = "Tải ảnh sản phẩm (đường dẫn lấy từ imageUrl)",
            description = "Cần đăng nhập: FE gọi bằng fetch kèm token rồi hiển thị qua URL.createObjectURL.")
    @GetMapping("/{id}/image/{fileName:.+}")
    public ResponseEntity<Resource> image(@PathVariable Integer id, @PathVariable String fileName) {
        ProductImageService.StoredImage img = images.load(id, fileName);
        return ResponseEntity.ok().contentType(img.mediaType())
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(img.path()));
    }

    @Operation(summary = "Xuất danh sách sản phẩm ra .xlsx (cùng bộ lọc với danh sách, tối đa 10.000 dòng)")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer groupId, @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) Boolean hasSerial,
            @RequestParam(required = false) String sort) {
        Sort order = PageRequests.of(0, 1, sort, SORTABLE, DEFAULT_SORT).getSort();
        byte[] file = excel.export(new ProductService.Filter(keyword, groupId, minPrice, maxPrice, hasSerial), order);
        return xlsx(file, "san-pham-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx");
    }

    @Operation(summary = "Tải file mẫu nhập sản phẩm (.xlsx)")
    @GetMapping("/import/template")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ResponseEntity<byte[]> importTemplate() {
        return xlsx(excel.template(), "mau-nhap-san-pham.xlsx");
    }

    @Operation(summary = "Nhập sản phẩm từ .xlsx: mã mới thì tạo, mã đã có thì cập nhật",
            description = "Một dòng lỗi là không lưu dòng nào; lỗi trả trong error.details.errors theo số dòng.")
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<ProductImportResult> importFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(excel.importFile(file));
    }

    private static ResponseEntity<byte[]> xlsx(byte[] body, String fileName) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
