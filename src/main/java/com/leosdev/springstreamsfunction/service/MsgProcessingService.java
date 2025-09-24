package com.leosdev.springstreamsfunction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class MsgProcessingService {

    public Flux<Message<String>> processMessage(Flux<Message<String>> input) {
        return input.map(this::processAndReturn);
    }

    // Nuevo método para Consumer
    public Flux<Message<String>> consumeMessage(Flux<Message<String>> input) {
        return input.doOnNext(msg -> {
            String payload = msg.getPayload();
            log.info("Consumed message: {}", payload);

            if (payload.contains("error")) {
                throw new RuntimeException("Business logic failed");
            }
        });
    }

    private Message<String> processAndReturn(Message<String> message) {
        String payload = message.getPayload();
        String result = "PROCESSED: " + payload.toUpperCase();

        return MessageBuilder
             .withPayload(result)
             .copyHeaders(message.getHeaders())
             .build();
    }
}
