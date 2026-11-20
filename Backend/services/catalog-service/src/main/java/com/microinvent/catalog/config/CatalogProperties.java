package com.microinvent.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Cấu hình riêng catalog-service (prefix microinvent.catalog).
 *
 * @param encryptionKey khóa AES-256 (base64, 32 byte) mã hóa STK nhà cung cấp và CCCD khách hàng
 * @param imageDir thư mục lưu ảnh sản phẩm (mỗi tenant một thư mục con)
 * @param maxImageSize dung lượng ảnh tối đa
 * @param maxImportRows số dòng tối đa khi nhập sản phẩm từ Excel
 * @param maxExportRows số dòng tối đa khi xuất sản phẩm ra Excel
 */
@ConfigurationProperties(prefix = "microinvent.catalog")
public record CatalogProperties(String encryptionKey, String imageDir, DataSize maxImageSize, Integer maxImportRows,
        Integer maxExportRows) {

    public CatalogProperties {
        if (imageDir == null || imageDir.isBlank()) {
            imageDir = "./data/product-images";
        }
        if (maxImageSize == null) {
            maxImageSize = DataSize.ofMegabytes(2);
        }
        if (maxImportRows == null) {
            maxImportRows = 2000;
        }
        if (maxExportRows == null) {
            maxExportRows = 10000;
        }
    }
}
