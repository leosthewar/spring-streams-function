package com.leosdev.springstreamsfunction.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.FUNCTION_NAME_HEADER;
import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.MONITORING_ID_CONTEXT;
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

    return input
      .flatMap(msg -> {
        String monitoringId = UUID.randomUUID()
                                  .toString();
        log.info("Start processing message with monitoringId={} in function={}", monitoringId, functionName);
        //msg.getHeaders().put(MONITORING_ID_HEADER, monitoringId); //esto genera error porque los headers son inmutables
        var enrichMessage= MessageBuilder.fromMessage(msg)
                      .setHeader(MONITORING_ID_HEADER, monitoringId)
                      .setHeader(FUNCTION_NAME_HEADER, functionName)
                      .setHeader(SERVICE_NAME_HEADER, serviceName)
                      .build();
        sendMonitoringEvent("START", monitoringId, functionName, serviceName, null);
        
   

        return delegate.apply(Flux.just(enrichMessage))
                       .doOnNext(result -> {
                         log.info("Function {} processed successfully with monitoringId={}", functionName, monitoringId);
                         sendMonitoringEvent("OK", monitoringId, functionName, serviceName, null);
                       })
                       .onErrorContinue((error, failedMsg) -> {
                         log.error("Error during processing message with monitoringId={}. message:{}", monitoringId, failedMsg, error);
                         sendMonitoringEvent("ERROR", monitoringId, functionName, serviceName, error.getMessage());
                       })
                       .contextWrite(ctx -> ctx.put(MONITORING_ID_CONTEXT, monitoringId));
      }).onErrorContinue((error, failedMsg) -> {
        var monitoringId ="N/A";
        if(failedMsg instanceof Message<?> failedMessage) {
          monitoringId = failedMessage.getHeaders().getOrDefault(MONITORING_ID_HEADER, "N/A").toString();
        }
        log.error("Error during processing message with monitoringId={}. message:{}", monitoringId, failedMsg, error);
        sendMonitoringEvent("ERROR", monitoringId, functionName, serviceName, error.getMessage());
      });
  }


  public <T> Consumer<Flux<Message<T>>> wrap(
    String functionName,
    Function<Flux<Message<T>>, Flux<Message<T>>> delegate
  ) {
    return input -> wrap(functionName, input, delegate).subscribe();
  }

  private void sendMonitoringEvent(String status, String monitoringId, String functionName, String serviceName, String error) {
    MessageBuilder
      .withPayload(status)
      .setHeader(MONITORING_ID_HEADER, monitoringId)
      .setHeader(FUNCTION_NAME_HEADER, functionName)
      .setHeader(SERVICE_NAME_HEADER, serviceName)
      .setHeader("X-Error", error != null ? error : "")
      .build();

    log.info("Sending monitoring event: {} error: {}", status, error);
  }
}
