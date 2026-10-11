package com.aegis.demo.orders;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @GetMapping("/{id}")
    public OrderResponse getOrder(
            @PathVariable String id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return new OrderResponse(id, "PROCESSING", requestId, Instant.now());
    }

    @PostMapping
    public OrderResponse createOrder(
            @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return new OrderResponse(UUID.randomUUID().toString(), "ACCEPTED", requestId, Instant.now());
    }

    public record CreateOrderRequest(String sku, int quantity) {}

    public record OrderResponse(String id, String status, String requestId, Instant processedAt) {}
}
