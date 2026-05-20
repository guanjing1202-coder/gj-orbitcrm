package com.orbitcrm.message.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.core.message.NoticeEvent;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.message.api.NoticeCreateRequest;
import com.orbitcrm.message.api.NoticeResponse;
import com.orbitcrm.message.api.UnreadCountResponse;
import com.orbitcrm.message.service.NoticeEventPublisher;
import com.orbitcrm.message.service.NoticeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages/notices")
public class NoticeController {
    private final NoticeService noticeService;
    private final NoticeEventPublisher noticeEventPublisher;

    public NoticeController(NoticeService noticeService,
                            NoticeEventPublisher noticeEventPublisher) {
        this.noticeService = noticeService;
        this.noticeEventPublisher = noticeEventPublisher;
    }

    @GetMapping("/mine")
    @RequiresPermission("message:notice:view")
    public ApiResult<List<NoticeResponse>> myNotices(
            @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly) {
        return ApiResult.ok(noticeService.listMyNotices(unreadOnly));
    }

    @GetMapping("/mine/unread-count")
    @RequiresPermission("message:notice:view")
    public ApiResult<UnreadCountResponse> unreadCount() {
        return ApiResult.ok(new UnreadCountResponse(noticeService.unreadCount()));
    }

    @PatchMapping("/{id}/read")
    @RequiresPermission("message:notice:view")
    public ApiResult<NoticeResponse> markRead(@PathVariable("id") Long id) {
        return ApiResult.ok(noticeService.markRead(id));
    }

    @PostMapping
    @RequiresPermission("message:notice:manage")
    public ApiResult<NoticeResponse> createNotice(@Validated @RequestBody NoticeCreateRequest request) {
        return ApiResult.ok(noticeService.createNotice(request));
    }

    @PostMapping("/events")
    @RequiresPermission("message:notice:manage")
    public ApiResult<Boolean> publishNoticeEvent(@Validated @RequestBody NoticeEvent event) {
        noticeEventPublisher.publish(event);
        return ApiResult.ok(Boolean.TRUE);
    }
}
