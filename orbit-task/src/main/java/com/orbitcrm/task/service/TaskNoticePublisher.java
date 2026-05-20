package com.orbitcrm.task.service;

import com.orbitcrm.common.core.message.NoticeEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskNoticePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public TaskNoticePublisher(RabbitTemplate rabbitTemplate,
                               @Value("${orbit.message.notice-exchange}") String exchange,
                               @Value("${orbit.message.notice-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(NoticeEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
