package com.leosdev.springstreamsfunction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.util.UUID;

@Service
@Slf4j
public class MessageProcessingService {

    /**
     * Procesa mensajes enriquecidos
     */
    public Flux<String> processMessage(Flux<Message<String>> input) {
        return input
             .flatMap(msg -> Mono.deferContextual(ctx -> {
                 String monitoringIdProxy = ctx.getOrDefault("X-Monitoring-Id-Proxy", "N/A");
                 String monitoringIdService = ctx.getOrDefault("X-Monitoring-Id-Service", "N/A");

                     log.info("Processing with context - X-Monitoring-Id-Proxy: {}, X-Monitoring-Id -Service: {}", monitoringIdProxy, monitoringIdService);

                 return Mono.just(processMessageWithHeaders(msg));
             }));


    }

    private String processMessageWithHeaders(Message<String> message) {
        String payload = message.getPayload();

        String monitoringId = message.getHeaders().get("X-Monitoring-Id", String.class);
        String traceId = message.getHeaders().get("X-Trace-Id", String.class);
        String functionName = message.getHeaders().get("X-Function-Name", String.class);

        log.info("Processing message - MonitoringId: {}, TraceId: {}, Function: {}",
             monitoringId, traceId, functionName);

        return performBusinessLogic(payload, monitoringId, traceId);
    }

    private String performBusinessLogic(String payload, String monitoringId, String traceId) {
        log.debug("Executing business logic for payload: {} [{}]", payload, monitoringId);

        String result = "PROCESSED: " + payload.toUpperCase();
        throw  new RuntimeException("Business logic failed for trace: " + traceId);


    }

}
