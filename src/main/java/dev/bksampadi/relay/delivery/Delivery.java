package dev.bksampadi.relay.delivery;

import java.time.Instant;

public record Delivery(
        long id,
        long webhookEndpointId,
        String payload,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant leaseUntil,
        Instant createdAt,
        Instant updatedAt
) {
}