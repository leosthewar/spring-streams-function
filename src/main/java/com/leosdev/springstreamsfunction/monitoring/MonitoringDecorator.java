package com.leosdev.springstreamsfunction.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.function.Function;

import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.FUNCTION_NAME_HEADER;
import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.MONITORING_ID_HEADER;
import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.SERVICE_NAME_HEADER;


@Slf4j
@RequiredArgsConstructor
public class MonitoringDecorator {

    private final String serviceName;

    public <T> Flux<Message<T>> wrap(
         String functionName,
         Flux<Message<T>> input,
         Function<Flux<Message<T>>, Flux<Message<T>>> delegate) {

        String monitoringId = UUID.randomUUID().toString();

        sendMonitoringEvent("START", monitoringId, functionName, serviceName, null);

        return delegate.apply(input)
                       .doOnNext(result -> {
                           log.info("Function {} processed successfully with monitoringId={}", functionName, monitoringId);
                       })
                       .onErrorContinue((error, msg) -> {
                           log.error("Error during processing message. message:{}", msg,error);
                           sendMonitoringEvent("ERROR", monitoringId, functionName, serviceName, error.getMessage());
                       });
    }

    private void sendMonitoringEvent(String status, String monitoringId, String functionName, String serviceName, String error) {
        Message<String> message = MessageBuilder
             .withPayload(status)
             .setHeader(MONITORING_ID_HEADER, monitoringId)
             .setHeader(FUNCTION_NAME_HEADER, functionName)
             .setHeader(SERVICE_NAME_HEADER, serviceName)
             .setHeader("X-Error", error != null ? error : "")
             .build();

        log.info("Sending monitoring event: {} errror:{}",status,error  );

    }
}
