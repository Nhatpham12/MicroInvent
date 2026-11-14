package com.microinvent.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microinvent.common.exception.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    private static final Set<String> ALLOWED = Set.of("id", "name");

    @Test
    void parsesSortAndAddsIdTieBreaker() {
        Pageable p = PageRequests.of(1, 500, "name,DESC", ALLOWED, Sort.by("id"));
        assertThat(p.getPageNumber()).isEqualTo(1);
        assertThat(p.getPageSize()).isEqualTo(PageRequests.MAX_SIZE);
        assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "name").and(Sort.by("id")));
    }

    @Test
    void usesDefaultSortWhenMissing() {
        assertThat(PageRequests.of(0, 0, null, ALLOWED, Sort.by("name")).getSort()).isEqualTo(Sort.by("name"));
    }

    @Test
    void rejectsUnknownFieldDirectionAndNegativePage() {
        assertThatThrownBy(() -> PageRequests.of(0, 20, "password", ALLOWED, Sort.unsorted()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 20, "name,up", ALLOWED, Sort.unsorted()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PageRequests.of(-1, 20, null, ALLOWED, Sort.unsorted()))
                .isInstanceOf(BusinessException.class);
    }
}
