package com.engine.order.infrastructure.adapter.out.persistence.repository;

import com.engine.order.domain.model.DlqStatus;
import com.engine.order.infrastructure.adapter.out.persistence.entity.DlqMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DlqMessageJpaRepository extends JpaRepository<DlqMessageJpaEntity, String> {

    List<DlqMessageJpaEntity> findByStatusOrderByCreatedAtDesc(DlqStatus status);

    List<DlqMessageJpaEntity> findAllByOrderByCreatedAtDesc();
}
