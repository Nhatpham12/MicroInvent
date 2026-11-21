package com.microinvent.procurement.repository;

import com.microinvent.procurement.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEvent.Status status, Pageable pageable);
}
