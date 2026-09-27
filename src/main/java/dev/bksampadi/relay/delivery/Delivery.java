package dev.bksampadi.relay.delivery;

import java.time.Instant;

public record Delivery(
        long id,
        long webhookEndpointId,
        String payload,
        DeliveryStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}