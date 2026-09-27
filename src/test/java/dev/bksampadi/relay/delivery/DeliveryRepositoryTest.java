package dev.bksampadi.relay.delivery;

import dev.bksampadi.relay.TestcontainersConfiguration;
import dev.bksampadi.relay.webhook.WebhookEndpoint;
import dev.bksampadi.relay.webhook.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DeliveryRepositoryTest {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private WebhookEndpointRepository webhookEndpointRepository;

    @Test
    void createsPendingDelivery() {
        WebhookEndpoint endpoint = webhookEndpointRepository.create(
                "payments",
                "https://example.com/webhooks/payments"
        );

        Delivery delivery = deliveryRepository.createPending(
                endpoint.id(),
                """
                        {"event":"payment.completed", "payment_id":"pay_123"}
                        """
        );

        assertThat(delivery.id()).isPositive();
        assertThat(delivery.webhookEndpointId()).isEqualTo(endpoint.id());
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.payload()).contains("payment.completed");
        assertThat(delivery.payload()).contains("payment_id");
        assertThat(delivery.createdAt()).isNotNull();
        assertThat(delivery.updatedAt()).isNotNull();
    }

    @Test
    void retrievesDeliveryById() {

        WebhookEndpoint endpoint = webhookEndpointRepository.create(
                "orders",
                "https://example.com/webhooks/orders"
        );

        Delivery created = deliveryRepository.createPending(
                endpoint.id(),
                """
                        {"event":"order.created","order_id":"ord_456"}
                        """
        );

        Optional<Delivery> result =
                deliveryRepository.findById(created.id());

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
        Optional<Delivery> result =
                deliveryRepository.findById(Long.MAX_VALUE);

        assertThat(result).isEmpty();
    }
}