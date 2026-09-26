package dev.bksampadi.relay.webhook;

import dev.bksampadi.relay.TestcontainersConfiguration;
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
class WebhookEndpointRepositoryTest {

    @Autowired
    private WebhookEndpointRepository repository;

    @Test
    void createsAndRetrievesWebhookEndpoint() {
        WebhookEndpoint created = repository.create(
                "payments",
                "https://example.com/webhooks/payments"
        );

        Optional<WebhookEndpoint> retrieved =
                repository.findById(created.id());

        assertThat(retrieved).isPresent();

        WebhookEndpoint endpoint = retrieved.orElseThrow();

        assertThat(endpoint.id()).isEqualTo(created.id());
        assertThat(endpoint.name()).isEqualTo("payments");
        assertThat(endpoint.url())
                .isEqualTo("https://example.com/webhooks/payments");
        assertThat(endpoint.createdAt()).isNotNull();
    }

    @Test
    void returnsEmptyWhenWebhookEndpointDoesNotExist() {
        Optional<WebhookEndpoint> result =
                repository.findById(Long.MAX_VALUE);

        assertThat(result).isEmpty();
    }
}