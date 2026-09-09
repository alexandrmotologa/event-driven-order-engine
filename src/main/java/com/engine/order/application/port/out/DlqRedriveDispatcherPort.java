package com.engine.order.application.port.out;

public interface DlqRedriveDispatcherPort {

    void dispatch(String topic, String key, String payload);
}
