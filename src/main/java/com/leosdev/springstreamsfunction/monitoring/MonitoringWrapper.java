package com.leosdev.springstreamsfunction.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.FUNCTION_NAME_HEADER;
import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.MONITORING_ID_CONTEXT;
import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.MONITORING_ID_HEADER;
import static com.leosdev.springstreamsfunction.config.MonitoringHeaders.SERVICE_NAME_HEADER;

@Configuration
@Slf4j
public class MonitoringWrapper implements BeanPostProcessor {

  private final Set<String> streamFunctionBeans;


  public MonitoringWrapper(ApplicationContext applicationContext) {
    String streamFunctionProperty = applicationContext.getEnvironment()
                                                      .getProperty("spring.cloud.function.definition");

    this.streamFunctionBeans = Arrays.stream(streamFunctionProperty.split(";"))
                                     .map(String::trim)
                                     .filter(s -> !s.isEmpty())
                                     .collect(Collectors.toSet());
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
 /*   if (!streamFunctionBeans.contains(beanName) || !(bean instanceof Function)) {
      return bean;
    }*/

    if(!beanName.equals("processMessage")){
      return bean;
    }

    log.info("Creating wrapper for function: {}", beanName);

    return createReactiveMonitoringWrapper(bean, beanName);
  }

  private Object createReactiveMonitoringWrapper(Object bean, String beanName) {
    return Proxy.newProxyInstance(
      bean.getClass().getClassLoader(),
      bean.getClass().getInterfaces(),
      (proxy, method, args) -> {
        try {
          // Si el argumento es un Flux, interceptarlo ANTES
          if (args != null && args.length > 0 && args[0] instanceof Flux<?>) {
            Flux<?> inputFlux = (Flux<?>) args[0];

            // Aplicar monitoring al input ANTES de pasarlo al metodo original
            Flux<?> monitoredFlux = inputFlux.flatMap(message -> {
              String monitoringId = UUID.randomUUID().toString();
              log.info("Start processing message with monitoringId={} in function={}", monitoringId, beanName);
              Message<?> enrichMessage =null;
              if(message instanceof Message<?> msg) {
                //msg.getHeaders().put(MONITORING_ID_HEADER, monitoringId); //esto genera error porque los headers son inmutables
                enrichMessage = MessageBuilder.fromMessage(msg)
                                              .setHeader(MONITORING_ID_HEADER, monitoringId)
                                              .setHeader(FUNCTION_NAME_HEADER, "spring-streams-function")
                                              .setHeader(SERVICE_NAME_HEADER, beanName)
                                              .build();
              }
              sendMonitoringEvent("START", monitoringId, beanName, "spring-streams-function", null);
              return Flux.just(enrichMessage != null ? enrichMessage : message)
                         .contextWrite(Context.of(MONITORING_ID_CONTEXT, monitoringId));
            }).onErrorContinue((error, failedMsg) -> {
              var monitoringId ="N/A";
              if(failedMsg instanceof Message<?> failedMessage) {
                monitoringId = failedMessage.getHeaders().getOrDefault(MONITORING_ID_HEADER, "N/A").toString();
              }
              log.error("Error during processing message with monitoringId={}. message:{}", monitoringId, failedMsg, error);
              sendMonitoringEvent("ERROR", monitoringId, beanName, "spring-streams-function", error.getMessage());
            });

            // Reemplazar el argumento con el Flux monitoreado
            args[0] = monitoredFlux;
          }

          // AHORA invocar el metodo original con los argumentos modificados
          Object result = method.invoke(bean, args);

          // Si el resultado es un Flux, agregar monitoring de salida
          if (result instanceof Flux<?> flux) {
            return flux.doOnNext(r -> {
                         var monitoringId ="N/A";
                         if(r instanceof Message<?> msg) {
                           monitoringId = msg.getHeaders().getOrDefault(MONITORING_ID_HEADER, "N/A").toString();
                         }
                         log.info("Function {} processed successfully with monitoringId={}", beanName, monitoringId);

                         sendMonitoringEvent("OK", monitoringId, beanName, "spring-streams-function", null);
                       })
                       .onErrorContinue((error, failedMsg) -> {
                         var monitoringId ="N/A";
                         if(failedMsg instanceof Message<?> failedMessage) {
                           monitoringId = failedMessage.getHeaders().getOrDefault(MONITORING_ID_HEADER, "N/A").toString();
                         }
                         log.error("Error during processing message with monitoringId={}. message:{}", monitoringId, failedMsg, error);
                         sendMonitoringEvent("ERROR", monitoringId, beanName, "spring-streams-function", error.getMessage());
                       });
          }

          return result;
        } catch (Exception e) {
          log.error("Error creating wrapper for bean : {}", beanName, e);
          throw e;
        }
      }
    );
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