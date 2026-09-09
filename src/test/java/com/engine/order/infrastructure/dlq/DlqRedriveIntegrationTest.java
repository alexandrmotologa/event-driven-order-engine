package com.engine.order.infrastructure.dlq;

import com.engine.order.application.dto.DlqMessageDto;
import com.engine.order.application.dto.PatchDlqMessageCommand;
import com.engine.order.application.port.out.DlqPort;
import com.engine.order.domain.model.DlqStatus;
import com.engine.order.infrastructure.config.KafkaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaConfig.ORDER_EVENTS_TOPIC,
                KafkaConfig.INVENTORY_COMMANDS_TOPIC,
                KafkaConfig.INVENTORY_REPLIES_TOPIC,
                KafkaConfig.PAYMENT_COMMANDS_TOPIC,
                KafkaConfig.PAYMENT_REPLIES_TOPIC,
                KafkaConfig.ORDER_EVENTS_DLQ_TOPIC
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=false",
        "outbox.relay.enabled=false",
        "saga.timeout.enabled=false"
})
@DirtiesContext
class DlqRedriveIntegrationTest {

    @Autowired
    private DlqPort dlqPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should capture, query, redrive, patch, and discard DLQ poison pill messages")
    void shouldManageDlqLifecycleSuccessfully() {
        String msgId1 = UUID.randomUUID().toString();
        String orderId1 = UUID.randomUUID().toString();
        String payload1 = "{\"orderId\":\"" + orderId1 + "\",\"event\":\"CorruptedOrderEvent\"}";

        // 1. Capture message into DLQ
        dlqPort.captureMessage(
                msgId1,
                KafkaConfig.ORDER_EVENTS_TOPIC,
                0,
                100L,
                orderId1,
                payload1,
                "SerializationException",
                "Cannot parse invalid payload"
        );

        // 2. Query messages via REST API
        ResponseEntity<List<DlqMessageDto>> listResponse = restTemplate.exchange(
                "/api/v1/dlq/messages?status=PENDING",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody()).anyMatch(m -> m.id().equals(msgId1) && m.status().equals("PENDING"));

        // 3. Redrive message
        ResponseEntity<DlqMessageDto> redriveResponse = restTemplate.postForEntity(
                "/api/v1/dlq/" + msgId1 + "/redrive",
                null,
                DlqMessageDto.class
        );

        assertThat(redriveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redriveResponse.getBody()).isNotNull();
        assertThat(redriveResponse.getBody().status()).isEqualTo("REPLAYED");
        assertThat(redriveResponse.getBody().retryCount()).isGreaterThanOrEqualTo(1);

        // 4. Capture second message and patch & redrive
        String msgId2 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();
        dlqPort.captureMessage(
                msgId2,
                KafkaConfig.ORDER_EVENTS_TOPIC,
                0,
                101L,
                orderId2,
                "{\"invalid\":true}",
                "ValidationException",
                "Schema validation failed"
        );

        String fixedPayload = "{\"orderId\":\"" + orderId2 + "\",\"fixed\":true}";
        ResponseEntity<DlqMessageDto> patchResponse = restTemplate.postForEntity(
                "/api/v1/dlq/" + msgId2 + "/patch-and-retry",
                new PatchDlqMessageCommand(fixedPayload),
                DlqMessageDto.class
        );

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResponse.getBody()).isNotNull();
        assertThat(patchResponse.getBody().status()).isEqualTo("REPLAYED");
        assertThat(patchResponse.getBody().payload()).isEqualTo(fixedPayload);

        // 5. Capture third message and discard
        String msgId3 = UUID.randomUUID().toString();
        dlqPort.captureMessage(
                msgId3,
                KafkaConfig.ORDER_EVENTS_TOPIC,
                0,
                102L,
                UUID.randomUUID().toString(),
                "{\"fatal\":true}",
                "FatalException",
                "Permanent poison pill"
        );

        ResponseEntity<Void> discardResponse = restTemplate.exchange(
                "/api/v1/dlq/" + msgId3,
                HttpMethod.DELETE,
                null,
                Void.class
        );

        assertThat(discardResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        DlqMessageDto discarded = dlqPort.findById(msgId3).orElseThrow();
        assertThat(discarded.status()).isEqualTo(DlqStatus.DISCARDED.name());
    }
}
