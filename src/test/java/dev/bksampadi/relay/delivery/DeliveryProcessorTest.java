package dev.bksampadi.relay.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.bksampadi.relay.TestcontainersConfiguration;
import dev.bksampadi.relay.webhook.WebhookEndpoint;
import dev.bksampadi.relay.webhook.WebhookEndpointRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeliveryProcessorTest {

  @Autowired private DeliveryRepository deliveryRepository;

  @Autowired private DeliveryClaimService deliveryClaimService;

  @Autowired private DeliveryProcessor deliveryProcessor;

  @Autowired private WebhookEndpointRepository webhookEndpointRepository;

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void marksDeliverySucceededWhenWebhookReturnsSuccess() throws IOException {
    String url = startServer(204);

    WebhookEndpoint endpoint = webhookEndpointRepository.create("success-endpoint", url);

    Delivery pending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                            {"event":"payment.completed","payment_id":"pay_success"}
                            """);

    Delivery claimed = deliveryClaimService.claimNext().orElseThrow();

    Delivery result = deliveryProcessor.process(claimed);

    assertThat(result.id()).isEqualTo(pending.id());
    assertThat(result.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
    assertThat(result.attemptCount()).isEqualTo(1);
    assertThat(result.leaseUntil()).isNull();

    Delivery persisted = deliveryRepository.findById(pending.id()).orElseThrow();

    assertThat(persisted.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
    assertThat(persisted.attemptCount()).isEqualTo(1);
    assertThat(persisted.leaseUntil()).isNull();
  }

  @Test
  void reschedulesDeliveryWhenWebhookReturnsServerError() throws IOException {
    String url = startServer(500);

    WebhookEndpoint endpoint = webhookEndpointRepository.create("failing-endpoint", url);

    Delivery pending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                            {"event":"payment.failed","payment_id":"pay_retry"}
                            """);

    Delivery claimed = deliveryClaimService.claimNext().orElseThrow();

    Delivery result = deliveryProcessor.process(claimed);

    assertThat(result.id()).isEqualTo(pending.id());
    assertThat(result.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(result.attemptCount()).isEqualTo(1);
    assertThat(result.leaseUntil()).isNull();
    assertThat(Duration.between(result.updatedAt(), result.nextAttemptAt()))
        .isEqualTo(Duration.ofSeconds(30));

    Delivery persisted = deliveryRepository.findById(pending.id()).orElseThrow();

    assertThat(persisted.status()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(persisted.attemptCount()).isEqualTo(1);
    assertThat(persisted.leaseUntil()).isNull();
  }

  private String startServer(int statusCode) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);

    server.createContext(
        "/webhook",
        exchange -> {
          exchange.sendResponseHeaders(statusCode, -1);
          exchange.close();
        });

    server.start();

    return "http://localhost:" + server.getAddress().getPort() + "/webhook";
  }
}
