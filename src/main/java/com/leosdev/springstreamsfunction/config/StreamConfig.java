package com.leosdev.springstreamsfunction.config;

import com.leosdev.springstreamsfunction.monitoring.MonitoringDecorator;
import com.leosdev.springstreamsfunction.service.MsgProcessingService;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

@Configuration
public class StreamConfig {

    @Bean
    public Scheduler virtualScheduler() {
        return Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public MonitoringDecorator monitoringDecorator(StreamBridge streamBridge) {
        return new MonitoringDecorator("spring-streams-function");
    }


    /**
     * Function bean remains Flux<String> -> Flux<String>.
     * Proxy will convert input strings into enriched Message<String>.
     */
    /*
    @Bean
    public Function<Flux<Message<String>>, Flux<String>> processMessage(
         MessageProcessingService messageProcessingService,
         Scheduler virtualSchedulers) {

        return flux -> messageProcessingService
             .processMessage(flux.publishOn(virtualSchedulers))
             .contextWrite(Context.of("X-Monitoring-Id-Service", UUID.randomUUID().toString()));
    }

     */
    @Bean
    public Function<Flux<Message<String>>, Flux<Message<String>>> processMessageDecorator(
         MsgProcessingService messageProcessingService,
         Scheduler virtualScheduler,
         MonitoringDecorator monitoringDecorator) {

        return flux -> monitoringDecorator.wrap(
             "processMessage",
             flux.publishOn(virtualScheduler),
             messageProcessingService::processMessage
        );
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeMessage(
         MsgProcessingService messageProcessingService,
         Scheduler virtualScheduler,
         MonitoringDecorator monitoringDecorator) {

        return flux -> monitoringDecorator.wrap(
             "consumeMessage",
             flux.publishOn(virtualScheduler),
             messageProcessingService::consumeMessage
        ).subscribe();
    }


}
