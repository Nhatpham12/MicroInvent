package com.microinvent.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.catalog.config.CatalogProperties;
import com.microinvent.catalog.domain.Product;
import com.microinvent.catalog.repository.ProductRepository;
import com.microinvent.common.context.TenantContext;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class ProductImageServiceTest {

    private final CatalogAudit audit = org.mockito.Mockito.mock(CatalogAudit.class);

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};

    @TempDir
    Path dir;

    private ProductRepository products;
    private ProductImageService service;
    private Product product;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        service = new ProductImageService(products,
                new CatalogProperties("k", dir.toString(), DataSize.ofKilobytes(1), null, null), "tigernet", audit);
        product = new Product();
        product.setId(5);
        when(products.findForUpdateByIdAndDeletedFalse(5)).thenReturn(Optional.of(product));
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
    void uploadStoresPerTenantAndReplacesOldFile() throws Exception {
        String first = service.upload(5, new MockMultipartFile("file", "a.png", "image/png", PNG));
        assertThat(first).matches("/api/v1/products/5/image/5-[0-9a-f]{12}\\.png");
        String firstName = first.substring(first.lastIndexOf('/') + 1);
        assertThat(dir.resolve("acme").resolve(firstName)).exists();

        String second = service.upload(5, new MockMultipartFile("file", "b.png", "image/png", PNG));
        assertThat(product.getImageUrl()).isEqualTo(second);
        assertThat(dir.resolve("acme").resolve(firstName)).doesNotExist();

        ProductImageService.StoredImage img = service.load(5, second.substring(second.lastIndexOf('/') + 1));
        assertThat(img.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(Files.readAllBytes(img.path())).isEqualTo(PNG);
        // Upload luôn khóa dòng sản phẩm (BUG-07: upload song song không để file mồ côi)
        verify(products, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void rejectsFakeImagesAndTooLargeFiles() {
        assertThat(code(() -> service.upload(5,
                new MockMultipartFile("file", "x.png", "image/png", "<svg onload=alert(1)>".getBytes()))))
                .isEqualTo(ErrorCode.FILE_INVALID);
        byte[] big = new byte[2048];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThat(code(() -> service.upload(5, new MockMultipartFile("file", "x.png", "image/png", big))))
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
        assertThat(code(() -> service.upload(6, new MockMultipartFile("file", "x.png", "image/png", PNG))))
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void loadRejectsPathTraversalAndOtherProductsFiles() {
        assertThat(code(() -> service.load(5, "../../etc/passwd"))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(code(() -> service.load(5, "6-0123456789ab.png"))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(code(() -> service.load(5, "5-0123456789ab.png"))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void detectsJpegAndWebp() {
        assertThat(ProductImageService.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}))
                .isEqualTo(ProductImageService.ImageType.JPEG);
        assertThat(ProductImageService.detect("RIFF\0\0\0\0WEBP".getBytes()))
                .isEqualTo(ProductImageService.ImageType.WEBP);
        assertThat(ProductImageService.detect(new byte[0])).isNull();
    }
}
