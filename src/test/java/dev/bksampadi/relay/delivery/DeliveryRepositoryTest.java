package dev.bksampadi.relay.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bksampadi.relay.TestcontainersConfiguration;
import dev.bksampadi.relay.webhook.WebhookEndpoint;
import dev.bksampadi.relay.webhook.WebhookEndpointRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DeliveryRepositoryTest {

  @Autowired private DeliveryRepository deliveryRepository;

  @Autowired private WebhookEndpointRepository webhookEndpointRepository;

  @Test
  void createsPendingDelivery() {
    WebhookEndpoint endpoint =
        webhookEndpointRepository.create("payments", "https://example.com/webhooks/payments");

    Delivery delivery =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                        {"event":"payment.completed", "payment_id":"pay_123"}
                        """);

    assertThat(delivery.id()).isPositive();
    assertThat(delivery.webhookEndpointId()).isEqualTo(endpoint.id());
    assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(delivery.payload()).contains("payment.completed");
    assertThat(delivery.payload()).contains("payment_id");
    assertThat(delivery.createdAt()).isNotNull();
    assertThat(delivery.updatedAt()).isNotNull();
    assertThat(delivery.attemptCount()).isZero();
    assertThat(delivery.nextAttemptAt()).isNotNull();
    assertThat(delivery.leaseUntil()).isNull();
  }

  @Test
  void retrievesDeliveryById() {

    WebhookEndpoint endpoint =
        webhookEndpointRepository.create("orders", "https://example.com/webhooks/orders");

    Delivery created =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                        {"event":"order.created","order_id":"ord_456"}
                        """);

    Optional<Delivery> result = deliveryRepository.findById(created.id());

    assertThat(result).isPresent();

    Delivery retrieved = result.orElseThrow();

    assertThat(retrieved.id()).isEqualTo(created.id());
    assertThat(retrieved.webhookEndpointId()).isEqualTo(endpoint.id());
    assertThat(retrieved.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(retrieved.payload()).contains("order.created");
    assertThat(retrieved.payload()).contains("order_id");
    assertThat(retrieved.createdAt()).isNotNull();
    assertThat(retrieved.updatedAt()).isNotNull();
  }

  @Test
  void returnsEmptyWhenDeliveryDoesNotExist() {
    Optional<Delivery> result = deliveryRepository.findById(Long.MAX_VALUE);

    assertThat(result).isEmpty();
  }

  @Test
  void claimsNextReadyDelivery() {
    WebhookEndpoint endpoint =
        webhookEndpointRepository.create("payments", "https://example.com/webhooks/payments");

    Delivery pending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                        {"event":"payment.completed","payment_id":"pay_789"}
                        """);

    Instant beforeClaim = Instant.now();

    Optional<Delivery> result = deliveryRepository.claimNextReady(Duration.ofSeconds(30));

    assertThat(result).isPresent();

    Delivery claimed = result.orElseThrow();

    assertThat(claimed.id()).isEqualTo(pending.id());
    assertThat(claimed.status()).isEqualTo(DeliveryStatus.PROCESSING);
    assertThat(claimed.attemptCount()).isEqualTo(1);
    assertThat(claimed.leaseUntil()).isNotNull();
    assertThat(claimed.leaseUntil()).isAfter(beforeClaim);

    Delivery persisted = deliveryRepository.findById(pending.id()).orElseThrow();

    assertThat(persisted.status()).isEqualTo(DeliveryStatus.PROCESSING);
    assertThat(persisted.attemptCount()).isEqualTo(1);
    assertThat(persisted.leaseUntil()).isNotNull();
  }

  @Test
  void reschedulesProcessingDelivery() {
    WebhookEndpoint endpoint =
        webhookEndpointRepository.create("payments", "https://example.com/webhooks/payments");

    Delivery pending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                                {"event":"payment.failed","payment_id":"pay_retry"}
                                """);

    Delivery claimed = deliveryRepository.claimNextReady(Duration.ofSeconds(30)).orElseThrow();

    Optional<Delivery> result = deliveryRepository.reschedule(claimed.id(), Duration.ofSeconds(30));

    assertThat(result).isPresent();

    Delivery rescheduled = result.orElseThrow();

    assertThat(rescheduled.id()).isEqualTo(pending.id());
    assertThat(rescheduled.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(rescheduled.attemptCount()).isEqualTo(1);
    assertThat(rescheduled.leaseUntil()).isNull();
    assertThat(Duration.between(rescheduled.updatedAt(), rescheduled.nextAttemptAt()))
        .isEqualTo(Duration.ofSeconds(30));

    Delivery persisted = deliveryRepository.findById(pending.id()).orElseThrow();

    assertThat(persisted.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(persisted.attemptCount()).isEqualTo(1);
    assertThat(persisted.leaseUntil()).isNull();
  }

  @Test
  void doesNotReschedulePendingDelivery() {
    WebhookEndpoint endpoint =
        webhookEndpointRepository.create("orders", "https://example.com/webhooks/orders");

    Delivery pending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                                {"event":"order.created","order_id":"ord_pending"}
                                """);

    Optional<Delivery> result = deliveryRepository.reschedule(pending.id(), Duration.ofSeconds(30));

    assertThat(result).isEmpty();

    Delivery persisted = deliveryRepository.findById(pending.id()).orElseThrow();

    assertThat(persisted.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(persisted.attemptCount()).isZero();
    assertThat(persisted.leaseUntil()).isNull();
  }

  @Test
  void marksProcessingDeliverySucceeded() {
    WebhookEndpoint endpoint =
        webhookEndpointRepository.create("payments", "https://example.com/webhooks/payments");

    Delivery pending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                                {"event":"payment.completed","payment_id":"pay_success"}
                                """);

    Delivery claimed = deliveryRepository.claimNextReady(Duration.ofSeconds(30)).orElseThrow();

    Optional<Delivery> result = deliveryRepository.markSucceeded(claimed.id());

    assertThat(result).isPresent();

    Delivery succeeded = result.orElseThrow();

    assertThat(succeeded.id()).isEqualTo(pending.id());
    assertThat(succeeded.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
    assertThat(succeeded.attemptCount()).isEqualTo(1);
    assertThat(succeeded.leaseUntil()).isNull();

    Delivery persisted = deliveryRepository.findById(pending.id()).orElseThrow();

    assertThat(persisted.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
    assertThat(persisted.attemptCount()).isEqualTo(1);
    assertThat(persisted.leaseUntil()).isNull();
  }

  @Test
  void doesNotMarkPendingDeliverySucceeded() {
    WebhookEndpoint endpoint =
        webhookEndpointRepository.create("orders", "https://example.com/webhooks/orders");

    Delivery pending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                                {"event":"order.created","order_id":"ord_not_claimed"}
                                """);

    Optional<Delivery> result = deliveryRepository.markSucceeded(pending.id());

    assertThat(result).isEmpty();

    Delivery persisted = deliveryRepository.findById(pending.id()).orElseThrow();

    assertThat(persisted.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(persisted.attemptCount()).isZero();
  }

  @Test
  void returnsEmptyWhenNoDeliveryIsReady() {
    Optional<Delivery> result = deliveryRepository.claimNextReady(Duration.ofSeconds(30));

    assertThat(result).isEmpty();
  }
}
