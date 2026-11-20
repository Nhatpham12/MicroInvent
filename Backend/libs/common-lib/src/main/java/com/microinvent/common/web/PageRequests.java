package com.microinvent.common.web;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

/**
 * Dựng Pageable từ query ?page=0&size=20&sort=name,asc với whitelist trường sắp xếp (trường lạ → 400 thay vì
 * lỗi 500 của Spring Data). size bị chặn trong [1, 100].
 */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowed, Sort defaultSort) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "page phải ≥ 0");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(page, safeSize, parseSort(sort, allowed, defaultSort));
    }

    static Sort parseSort(String sort, Set<String> allowed, Sort defaultSort) {
        if (!StringUtils.hasText(sort)) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!allowed.contains(field) || parts.length > 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Không sắp xếp được theo '" + field + "'",
                    Map.of("allowed", allowed.stream().sorted().toList()));
        }
        String dir = parts.length == 2 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (!dir.equals("asc") && !dir.equals("desc")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Chiều sắp xếp chỉ nhận asc hoặc desc");
        }
        Sort primary = Sort.by(dir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, field);
        // Thêm id để thứ tự ổn định khi trùng giá trị (phân trang không lặp/sót dòng).
        return field.equals("id") ? primary : primary.and(Sort.by("id"));
    }
}
