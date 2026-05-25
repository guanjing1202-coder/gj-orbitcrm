package com.orbitcrm.message.service;

import com.orbitcrm.common.core.message.NoticeEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NoticeEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public NoticeEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${orbit.message.notice-exchange}") String exchange,
                                @Value("${orbit.message.notice-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(NoticeEvent event) {
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notice event is required");
        }
        if (!StringUtils.hasText(event.getTenantCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantCode is required");
        }
        if (!StringUtils.hasText(event.getTitle()) || !StringUtils.hasText(event.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title and content are required");
        }
        normalizeTextFields(event);
        if (event.getReceiverUserIds() != null) {
            for (Long receiverUserId : event.getReceiverUserIds()) {
                validateReceiverUserId(receiverUserId);
            }
        }
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }

    private void normalizeTextFields(NoticeEvent event) {
        event.setTenantCode(event.getTenantCode().trim());
        event.setTitle(event.getTitle().trim());
        event.setContent(event.getContent().trim());
        if (StringUtils.hasText(event.getNoticeType())) {
            event.setNoticeType(event.getNoticeType().trim());
        } else {
            event.setNoticeType(null);
        }
    }

    private void validateReceiverUserId(Long receiverUserId) {
        if (receiverUserId == null || receiverUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiver user id is required");
        }
    }
}
