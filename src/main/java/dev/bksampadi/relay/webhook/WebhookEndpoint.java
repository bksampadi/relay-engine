package dev.bksampadi.relay.webhook;

import java.time.Instant;

public record WebhookEndpoint(long id, String name, String url, Instant createdAt) {}
