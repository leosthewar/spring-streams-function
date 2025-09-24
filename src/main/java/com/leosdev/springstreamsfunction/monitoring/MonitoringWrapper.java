package com.leosdev.springstreamsfunction.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class MonitoringWrapper implements BeanPostProcessor {

    private final Set<String> streamFunctionBeans;
    private static final String MONITORING_ID_HEADER = "X-Monitoring-Id-Proxy";
    private static final String FUNCTION_NAME_HEADER = "X-Function-Name";
    private static final String SERVICE_NAME_HEADER = "X-Service-Name";


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
        if (!streamFunctionBeans.contains(beanName) || !(bean instanceof Function)) {
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
                     Object result = method.invoke(bean, args);
                     if (result instanceof Flux<?>) {
                         return ((Flux<?>) result)
                              .doOnEach(
                                   signal -> signal.getContextView()
                                                   .<String>getOrEmpty(MONITORING_ID_HEADER)
                                                   .ifPresent(monitoringId -> {
                                                       if (signal.isOnNext()) {
                                                           log.info("process monitoringId:{} completed successfully", monitoringId);
                                                           // TODO: send notification

                                                       } else if (signal.isOnError()) {
                                                           log.error("Error during processing message. monitorId:{}, object:{}, error:{}",
                                                                monitoringId, signal.get(), signal.getThrowable());
                                                       }
                                                   }))
                              .onErrorContinue((error,msg)-> {
                                  // if message is of type Message, try to get the monitoringId from the headers
                                  if(msg instanceof Message<?> message){
                                      log.error("Error during processing message. message:{}", message.getHeaders().get(MONITORING_ID_HEADER));
                                      // TODO: send notification
                                  }
                              })
                              .contextWrite(Context.of(MONITORING_ID_HEADER, UUID.randomUUID().toString()));
                     }
                     return result;

                 } catch (Exception e) {
                     log.error("Error creating wrapper for bean : {}", beanName, e);
                     throw e;
                 }

             }
        );
    }

}