package com.engine.order.infrastructure.adapter.out.persistence;

import com.engine.order.application.dto.DlqMessageDto;
import com.engine.order.application.port.out.DlqPort;
import com.engine.order.domain.model.DlqStatus;
import com.engine.order.infrastructure.adapter.out.persistence.entity.DlqMessageJpaEntity;
import com.engine.order.infrastructure.adapter.out.persistence.repository.DlqMessageJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class DlqMessageRepositoryAdapter implements DlqPort {

    private final DlqMessageJpaRepository repository;

    public DlqMessageRepositoryAdapter(DlqMessageJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    @Transactional
    public void captureMessage(
            String id,
            String originalTopic,
            int partitionNum,
            long offsetNum,
            String messageKey,
            String payload,
            String exceptionClass,
            String errorMessage
    ) {
        Instant now = Instant.now();
        DlqMessageJpaEntity entity = new DlqMessageJpaEntity(
                id,
                originalTopic,
                partitionNum,
                offsetNum,
                messageKey,
                payload,
                exceptionClass,
                errorMessage,
                DlqStatus.PENDING,
                0,
                now,
                now
        );
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlqMessageDto> findByStatus(DlqStatus status) {
        return repository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlqMessageDto> findAll() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DlqMessageDto> findById(String id) {
        return repository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional
    public DlqMessageDto updateStatusAndPayload(String id, DlqStatus newStatus, String payload, boolean incrementRetry) {
        DlqMessageJpaEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DLQ Message not found: " + id));

        entity.setStatus(newStatus);
        if (payload != null && !payload.isBlank()) {
            entity.setPayload(payload);
        }
        if (incrementRetry) {
            entity.incrementRetryCount();
        }

        DlqMessageJpaEntity saved = repository.save(entity);
        return toDto(saved);
    }

    private DlqMessageDto toDto(DlqMessageJpaEntity entity) {
        return new DlqMessageDto(
                entity.getId(),
                entity.getOriginalTopic(),
                entity.getPartitionNum(),
                entity.getOffsetNum(),
                entity.getMessageKey(),
                entity.getPayload(),
                entity.getExceptionClass(),
                entity.getErrorMessage(),
                entity.getStatus().name(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
