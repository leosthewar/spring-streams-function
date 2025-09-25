package com.leosdev.springstreamsfunction.service;

import com.leosdev.springstreamsfunction.config.MonitoringHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.MONITORING_ID_CONTEXT;

@Service
@Slf4j
public class MessageProcessingService {

  /*
  Simple example
   */
  public Flux<Message<String>> processMessageSimple(Flux<Message<String>> input) {
    return input.map(this::processAndReturn);
  }


  public Flux<Message<String>> processMessage(Flux<Message<String>> input) {
    return input
      .flatMap(msg -> Mono.deferContextual(ctx -> {
        var monitoringIdContext = ctx.getOrDefault(MONITORING_ID_CONTEXT, "N/A");
        var monitoringIdHeader = msg.getHeaders().getOrDefault(MonitoringHeaders.MONITORING_ID_HEADER, "N/A");
        log.info("Processing with context - monitoringIdContext: {}, monitoringIdHeader: {}", monitoringIdContext, monitoringIdHeader);

        return Mono.just(processAndReturn(msg));
      }))
      .doOnNext(result -> log.info("Processed result: {}", result));
  }


  public void consumeMessageVoid(Message<String> input) {
    processConsume(input);
  }

  private void processConsume(Message<String> message) {
    String payload = message.getPayload();
    if (payload.contains("error")) {
      throw new RuntimeException("Business logic failed");
    }
    log.info("CONSUMED: {}", payload.toUpperCase());
  }

  private Message<String> processAndReturn(Message<String> message) {
    String payload = message.getPayload();
    String result = "PROCESSED: " + payload.toUpperCase();
    throw new RuntimeException("Business logic failed");
    /*
    return MessageBuilder
      .withPayload(result)
      .copyHeaders(message.getHeaders())
      .build();
      */
  }
}
