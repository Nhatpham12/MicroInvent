package com.microinvent.catalog.service;

import com.microinvent.catalog.config.CatalogProperties;
import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.common.context.TenantContext;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh sản phẩm: lưu file trên đĩa ({@code microinvent.catalog.image-dir/<tenant>/}), cột image_url giữ đường dẫn
 * tải {@code /api/v1/products/{id}/image/{file}}. Tên file đổi mỗi lần upload nên trình duyệt cache an toàn.
 * Chỉ nhận PNG / JPEG / WEBP, kiểm bằng magic bytes chứ không tin Content-Type client gửi.
 */
@Service
@Transactional
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
    private static final Pattern FILE_NAME = Pattern.compile("^(\\d+)-[0-9a-f]{12}\\.(png|jpg|webp)$");
    private static final Pattern SAFE_TENANT = Pattern.compile("^[A-Za-z0-9_\\-]{1,50}$");

    private final ProductRepository products;
    private final CatalogProperties props;
    private final String defaultTenant;

    public ProductImageService(ProductRepository products, CatalogProperties props,
            @Value("${microinvent.tenant.default-tenant:tigernet}") String defaultTenant) {
        this.products = products;
        this.props = props;
        this.defaultTenant = defaultTenant;
    }

    /** Ảnh đã lưu: đường dẫn file + kiểu nội dung. */
    public record StoredImage(Path path, MediaType mediaType) {
    }

    enum ImageType {
        PNG("png", MediaType.IMAGE_PNG), JPEG("jpg", MediaType.IMAGE_JPEG),
        WEBP("webp", MediaType.parseMediaType("image/webp"));

        final String ext;
        final MediaType mediaType;

        ImageType(String ext, MediaType mediaType) {
            this.ext = ext;
            this.mediaType = mediaType;
        }

        static ImageType fromExt(String ext) {
            return Arrays.stream(values()).filter(t -> t.ext.equals(ext)).findFirst().orElseThrow();
        }
    }

    public String upload(Integer productId, MultipartFile file) {
        Product p = products.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Vui lòng chọn ảnh");
        }
        if (file.getSize() > props.maxImageSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "Ảnh tối đa " + props.maxImageSize().toMegabytes() + " MB");
        }
        ImageType type;
        try (InputStream in = file.getInputStream()) {
            type = detect(in.readNBytes(12));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (type == null) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Chỉ nhận ảnh PNG, JPEG hoặc WEBP");
        }

        String fileName = productId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "."
                + type.ext;
        Path dir = tenantDir();
        Path target = dir.resolve(fileName);
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".upload-", ".tmp");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Không lưu được ảnh", ex);
        }

        String oldUrl = p.getImageUrl();
        String url = "/api/v1/products/" + productId + "/image/" + fileName;
        p.setImageUrl(url);
        // Xóa ảnh cũ sau khi commit; rollback thì xóa ảnh mới để không để rác.
        afterCompletion(committed -> deleteQuietly(committed ? fileNameOf(oldUrl) : fileName, dir));
        return url;
    }

    @Transactional(readOnly = true)
    public StoredImage load(Integer productId, String fileName) {
        Matcher m = FILE_NAME.matcher(fileName == null ? "" : fileName);
        if (!m.matches() || !m.group(1).equals(String.valueOf(productId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ảnh");
        }
        Path path = tenantDir().resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ảnh");
        }
        return new StoredImage(path, ImageType.fromExt(m.group(2)).mediaType);
    }

    /** Nhận diện ảnh qua magic bytes. */
    static ImageType detect(byte[] h) {
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return ImageType.PNG;
        }
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W'
                && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }

    Path tenantDir() {
        String tenant = TenantContext.get() != null ? TenantContext.get() : defaultTenant;
        if (!SAFE_TENANT.matcher(tenant).matches()) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }
        return Path.of(props.imageDir()).toAbsolutePath().normalize().resolve(tenant);
    }

    static String fileNameOf(String url) {
        if (url == null) {
            return null;
        }
        String name = url.substring(url.lastIndexOf('/') + 1);
        return FILE_NAME.matcher(name).matches() ? name : null;
    }

    private static void deleteQuietly(String fileName, Path dir) {
        if (fileName == null) {
            return;
        }
        try {
            Files.deleteIfExists(dir.resolve(fileName));
        } catch (IOException ex) {
            log.warn("Không xóa được ảnh {}: {}", fileName, ex.getMessage());
        }
    }

    private static void afterCompletion(java.util.function.Consumer<Boolean> action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.accept(true);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status == STATUS_COMMITTED);
            }
        });
    }
}
