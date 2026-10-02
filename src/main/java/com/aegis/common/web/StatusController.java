package com.aegis.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusController {

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse("UP", "aegis");
    }

    public record StatusResponse(String status, String service) {}
}
