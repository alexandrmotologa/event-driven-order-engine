package com.engine.order.application.port.in;

import com.engine.order.application.dto.DlqMessageDto;

import java.util.List;

public interface DlqManagementUseCase {

    List<DlqMessageDto> getDlqMessages(String status);

    DlqMessageDto redriveMessage(String messageId);

    DlqMessageDto patchAndRedriveMessage(String messageId, String updatedPayload);

    void discardMessage(String messageId);
}
