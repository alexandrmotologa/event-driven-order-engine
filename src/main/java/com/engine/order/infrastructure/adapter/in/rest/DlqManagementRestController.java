package com.engine.order.infrastructure.adapter.in.rest;

import com.engine.order.application.dto.DlqMessageDto;
import com.engine.order.application.dto.PatchDlqMessageCommand;
import com.engine.order.application.port.in.DlqManagementUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/dlq", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Dead Letter Queue (DLQ) Management", description = "Endpoints for inspecting, re-driving, and discarding poison pills in the Dead Letter Queue")
public class DlqManagementRestController {

    private final DlqManagementUseCase dlqManagementUseCase;

    public DlqManagementRestController(DlqManagementUseCase dlqManagementUseCase) {
        this.dlqManagementUseCase = Objects.requireNonNull(dlqManagementUseCase, "dlqManagementUseCase must not be null");
    }

    @GetMapping("/messages")
    @Operation(summary = "List DLQ Messages", description = "Retrieves dead-lettered messages, optionally filtered by status (PENDING, REPLAYED, DISCARDED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Messages retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DlqMessageDto.class))))
    })
    public ResponseEntity<List<DlqMessageDto>> getDlqMessages(
            @Parameter(description = "Filter by status: PENDING, REPLAYED, DISCARDED")
            @RequestParam(required = false) String status
    ) {
        List<DlqMessageDto> messages = dlqManagementUseCase.getDlqMessages(status);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{id}/redrive")
    @Operation(summary = "Redrive DLQ Message", description = "Republishes the failed message back to its original destination topic and marks it as REPLAYED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message re-driven successfully",
                    content = @Content(schema = @Schema(implementation = DlqMessageDto.class))),
            @ApiResponse(responseCode = "404", description = "DLQ Message not found")
    })
    public ResponseEntity<DlqMessageDto> redriveMessage(
            @Parameter(description = "DLQ Message UUID") @PathVariable String id
    ) {
        DlqMessageDto redriven = dlqManagementUseCase.redriveMessage(id);
        return ResponseEntity.ok(redriven);
    }

    @PostMapping("/{id}/patch-and-retry")
    @Operation(summary = "Patch and Redrive Message", description = "Updates the payload of a poisoned message and republishes it to the original topic.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message patched and re-driven successfully",
                    content = @Content(schema = @Schema(implementation = DlqMessageDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "404", description = "DLQ Message not found")
    })
    public ResponseEntity<DlqMessageDto> patchAndRedriveMessage(
            @Parameter(description = "DLQ Message UUID") @PathVariable String id,
            @Valid @RequestBody PatchDlqMessageCommand command
    ) {
        DlqMessageDto redriven = dlqManagementUseCase.patchAndRedriveMessage(id, command.payload());
        return ResponseEntity.ok(redriven);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Discard Poison Pill Message", description = "Marks a dead-lettered message as DISCARDED without republishing.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Message discarded successfully"),
            @ApiResponse(responseCode = "404", description = "DLQ Message not found")
    })
    public ResponseEntity<Void> discardMessage(
            @Parameter(description = "DLQ Message UUID") @PathVariable String id
    ) {
        dlqManagementUseCase.discardMessage(id);
        return ResponseEntity.noContent().build();
    }
}
