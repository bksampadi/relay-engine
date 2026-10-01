package dev.bksampadi.relay.delivery;

import dev.bksampadi.relay.webhook.WebhookClient;
import dev.bksampadi.relay.webhook.WebhookEndpoint;
import dev.bksampadi.relay.webhook.WebhookEndpointRepository;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class DeliveryProcessor {

  private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

  private final DeliveryRepository deliveryRepository;
  private final WebhookEndpointRepository webhookEndpointRepository;
  private final WebhookClient webhookClient;

  public DeliveryProcessor(
      DeliveryRepository deliveryRepository,
      WebhookEndpointRepository webhookEndpointRepository,
      WebhookClient webhookClient) {
    this.deliveryRepository = deliveryRepository;
    this.webhookEndpointRepository = webhookEndpointRepository;
    this.webhookClient = webhookClient;
  }

  public Delivery process(Delivery delivery) {
    WebhookEndpoint endpoint =
        webhookEndpointRepository
            .findById(delivery.webhookEndpointId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Webhook endpoint not found: " + delivery.webhookEndpointId()));

    try {
      webhookClient.send(endpoint.url(), delivery.payload());
    } catch (RestClientException exception) {
      return deliveryRepository
          .reschedule(delivery.id(), RETRY_DELAY)
          .orElseThrow(
              () ->
                  new IllegalStateException("Delivery could not be rescheduled: " + delivery.id()));
    }

    return deliveryRepository
        .markSucceeded(delivery.id())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Delivery could not be marked succeeded: " + delivery.id()));
  }
}
