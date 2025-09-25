package com.leosdev.springstreamsfunction.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

//@Configuration
@Slf4j
public class MonitoringProxy implements BeanPostProcessor {

    private final Set<String> streamFunctionBeans;
    private static final String MONITORING_ID_HEADER = "X-Monitoring-Id";
    private static final String FUNCTION_NAME_HEADER = "X-Function-Name";
    private static final String SERVICE_NAME_HEADER = "X-Service-Name";


    public MonitoringProxy(ApplicationContext applicationContext) {
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

        log.info("Creating enrichment proxy for function: {}", beanName);

        return Proxy.newProxyInstance(
             bean.getClass().getClassLoader(),
             bean.getClass().getInterfaces(),
             new EnrichmentInvocationHandler(bean, beanName)
        );
    }

    private static class EnrichmentInvocationHandler implements java.lang.reflect.InvocationHandler {
        private final Object target;
        private final String beanName;

        public EnrichmentInvocationHandler(Object target, String beanName) {
            this.target = target;
            this.beanName = beanName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!"apply".equals(method.getName()) || args.length != 1) {
                return method.invoke(target, args);
            }
            log.info("Enriching messages for function: {}", beanName);
            Object input = args[0];
            if (input instanceof Flux<?>) {
                Flux<?> flux = (Flux<?>) input;
                // Enriquecer mensajes y crear un Flux con contexto persistente
                Flux<Message<String>> enrichedFlux = flux.flatMap(o -> {
                    String monitoringId = UUID.randomUUID().toString();
                    Message<String> enrichedMessage = (o instanceof Message)
                         ? enrichMessage(String.valueOf(((Message<?>) o).getPayload()), beanName, monitoringId)
                         : enrichMessage(String.valueOf(o), beanName, monitoringId);

                    return Flux.deferContextual(ctx -> {
                        log.info("Enriched message with headers: X-Monitoring-Id={} ", monitoringId);
                        return Flux.just(enrichedMessage);
                    }).contextWrite(Context.of("X-Monitoring-Id-Proxy", UUID.randomUUID().toString()));
                });

                // Invocar la función target
                return method.invoke(target, new Object[]{enrichedFlux});
            }

            return method.invoke(target, args);
        }

        private Message<String> enrichMessage(String payload, String functionName, String monitoringId) {
            return MessageBuilder
                 .withPayload(payload)
                 .setHeader(MONITORING_ID_HEADER, monitoringId)
                 .setHeader(FUNCTION_NAME_HEADER, functionName)
                 .setHeader(SERVICE_NAME_HEADER, "spring-streams-function")
                 .build();
        }
    }



}