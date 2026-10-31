package com.tickethub.event.controller;

import com.tickethub.event.dto.ShowInternalResponse;
import com.tickethub.event.service.ShowService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API. The gateway never routes /internal/**. */
@Hidden
@RestController
@RequestMapping("/internal/shows")
@RequiredArgsConstructor
public class InternalShowController {

    private final ShowService showService;

    @GetMapping("/{id}")
    public ShowInternalResponse get(@PathVariable Long id) {
        return showService.internalView(id);
    }
}
