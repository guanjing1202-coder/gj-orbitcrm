package com.orbitcrm.message.service;

import com.orbitcrm.common.core.message.NoticeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.ArgumentCaptor;

class NoticeEventPublisherTest {
    @Test
    void publishTrimsTextFieldsBeforeSendingMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode(" demo-company ");
        event.setTitle(" Renewal reminder ");
        event.setContent(" Contract renewal is due soon ");
        event.setNoticeType(" TASK ");
        event.setReceiverUserIds(Arrays.asList(18L, 19L));

        publisher.publish(event);

        ArgumentCaptor<NoticeEvent> captor = ArgumentCaptor.forClass(NoticeEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("notice.exchange"), eq("notice.created"), captor.capture());
        NoticeEvent publishedEvent = captor.getValue();
        assertEquals("demo-company", publishedEvent.getTenantCode());
        assertEquals("Renewal reminder", publishedEvent.getTitle());
        assertEquals("Contract renewal is due soon", publishedEvent.getContent());
        assertEquals("TASK", publishedEvent.getNoticeType());
    }

    @Test
    void publishClearsNoticeTypeWhenNoticeTypeIsBlank() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode("demo-company");
        event.setTitle("Renewal reminder");
        event.setContent("Contract renewal is due soon");
        event.setNoticeType("   ");

        publisher.publish(event);

        ArgumentCaptor<NoticeEvent> captor = ArgumentCaptor.forClass(NoticeEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("notice.exchange"), eq("notice.created"), captor.capture());
        assertEquals(null, captor.getValue().getNoticeType());
    }

    @Test
    void publishRejectsMissingEventBeforeSendingMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> publisher.publish(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishRejectsMissingTenantCodeBeforeSendingMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");
        NoticeEvent event = new NoticeEvent();
        event.setTitle("Renewal reminder");
        event.setContent("Contract renewal is due soon");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> publisher.publish(event));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishRejectsMissingTitleBeforeSendingMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode("demo-company");
        event.setContent("Contract renewal is due soon");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> publisher.publish(event));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishRejectsMissingContentBeforeSendingMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode("demo-company");
        event.setTitle("Renewal reminder");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> publisher.publish(event));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishRejectsNullReceiverIdBeforeSendingMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode("demo-company");
        event.setTitle("Renewal reminder");
        event.setContent("Contract renewal is due soon");
        event.setReceiverUserIds(Arrays.asList(18L, null));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> publisher.publish(event));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishRejectsNonPositiveReceiverIdBeforeSendingMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NoticeEventPublisher publisher = new NoticeEventPublisher(rabbitTemplate, "notice.exchange", "notice.created");
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode("demo-company");
        event.setTitle("Renewal reminder");
        event.setContent("Contract renewal is due soon");
        event.setReceiverUserIds(Arrays.asList(18L, 0L));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> publisher.publish(event));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(rabbitTemplate);
    }
}
