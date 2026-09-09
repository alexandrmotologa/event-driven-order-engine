package com.engine.order.application.service;

import com.engine.order.application.dto.DlqMessageDto;
import com.engine.order.application.port.in.DlqManagementUseCase;
import com.engine.order.application.port.out.DlqPort;
import com.engine.order.application.port.out.DlqRedriveDispatcherPort;
import com.engine.order.domain.model.DlqStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class DlqManagementService implements DlqManagementUseCase {

    private static final Logger log = LoggerFactory.getLogger(DlqManagementService.class);

    private final DlqPort dlqPort;
    private final DlqRedriveDispatcherPort redriveDispatcherPort;

    public DlqManagementService(DlqPort dlqPort, DlqRedriveDispatcherPort redriveDispatcherPort) {
        this.dlqPort = Objects.requireNonNull(dlqPort, "dlqPort must not be null");
        this.redriveDispatcherPort = Objects.requireNonNull(redriveDispatcherPort, "redriveDispatcherPort must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlqMessageDto> getDlqMessages(String status) {
        if (status == null || status.isBlank()) {
            return dlqPort.findAll();
        }
        try {
            DlqStatus dlqStatus = DlqStatus.valueOf(status.toUpperCase().trim());
            return dlqPort.findByStatus(dlqStatus);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid DLQ status filter provided: {}", status);
            return dlqPort.findAll();
        }
    }

    @Override
    public DlqMessageDto redriveMessage(String messageId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        DlqMessageDto message = dlqPort.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ Message not found: " + messageId));

        log.info("Redriving DLQ message [{}] back to topic [{}] with key [{}]",
                message.id(), message.originalTopic(), message.messageKey());

        redriveDispatcherPort.dispatch(message.originalTopic(), message.messageKey(), message.payload());

        return dlqPort.updateStatusAndPayload(message.id(), DlqStatus.REPLAYED, null, true);
    }

    @Override
    public DlqMessageDto patchAndRedriveMessage(String messageId, String updatedPayload) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(updatedPayload, "updatedPayload must not be null");

        DlqMessageDto message = dlqPort.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ Message not found: " + messageId));

        log.info("Patching and redriving DLQ message [{}] back to topic [{}] with key [{}]",
                message.id(), message.originalTopic(), message.messageKey());

        redriveDispatcherPort.dispatch(message.originalTopic(), message.messageKey(), updatedPayload);

        return dlqPort.updateStatusAndPayload(message.id(), DlqStatus.REPLAYED, updatedPayload, true);
    }

    @Override
    public void discardMessage(String messageId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        log.info("Discarding poison pill DLQ message [{}]", messageId);
        dlqPort.updateStatusAndPayload(messageId, DlqStatus.DISCARDED, null, false);
    }
}
