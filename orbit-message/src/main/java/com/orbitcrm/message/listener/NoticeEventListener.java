package com.orbitcrm.message.listener;

import com.orbitcrm.common.core.message.NoticeEvent;
import com.orbitcrm.message.service.NoticeService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NoticeEventListener {
    private final NoticeService noticeService;

    public NoticeEventListener(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @RabbitListener(queues = "${orbit.message.notice-queue}")
    public void handleNoticeEvent(NoticeEvent event) {
        noticeService.createNoticeFromEvent(event);
    }
}
