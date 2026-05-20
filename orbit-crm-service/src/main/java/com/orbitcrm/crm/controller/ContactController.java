package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.ContactCreateRequest;
import com.orbitcrm.crm.api.ContactResponse;
import com.orbitcrm.crm.api.ContactUpdateRequest;
import com.orbitcrm.crm.service.ContactService;
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
@RequestMapping("/api/crm/contacts")
public class ContactController {
    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    @RequiresPermission("crm:contact:manage")
    public ApiResult<List<ContactResponse>> listContacts(
            @RequestParam(value = "customerId", required = false) Long customerId) {
        return ApiResult.ok(contactService.listContacts(customerId));
    }

    @PostMapping
    @RequiresPermission("crm:contact:manage")
    public ApiResult<ContactResponse> createContact(@Validated @RequestBody ContactCreateRequest request) {
        return ApiResult.ok(contactService.createContact(request));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("crm:contact:manage")
    public ApiResult<ContactResponse> updateContact(@PathVariable("id") Long id,
                                                    @RequestBody ContactUpdateRequest request) {
        return ApiResult.ok(contactService.updateContact(id, request));
    }

    @PatchMapping("/{id}/primary")
    @RequiresPermission("crm:contact:manage")
    public ApiResult<ContactResponse> setPrimary(@PathVariable("id") Long id) {
        return ApiResult.ok(contactService.setPrimary(id));
    }
}
