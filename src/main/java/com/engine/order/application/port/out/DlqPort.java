package com.engine.order.application.port.out;

import com.engine.order.application.dto.DlqMessageDto;
import com.engine.order.domain.model.DlqStatus;

import java.util.List;
import java.util.Optional;

public interface DlqPort {

    void captureMessage(
            String id,
            String originalTopic,
            int partitionNum,
            long offsetNum,
            String messageKey,
            String payload,
            String exceptionClass,
            String errorMessage
    );

    List<DlqMessageDto> findByStatus(DlqStatus status);

    List<DlqMessageDto> findAll();

    Optional<DlqMessageDto> findById(String id);

    DlqMessageDto updateStatusAndPayload(String id, DlqStatus newStatus, String payload, boolean incrementRetry);
}
