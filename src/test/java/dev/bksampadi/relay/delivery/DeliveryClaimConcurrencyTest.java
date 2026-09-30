package dev.bksampadi.relay.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bksampadi.relay.TestcontainersConfiguration;
import dev.bksampadi.relay.webhook.WebhookEndpoint;
import dev.bksampadi.relay.webhook.WebhookEndpointRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeliveryClaimConcurrencyTest {

  @Autowired private DeliveryRepository deliveryRepository;

  @Autowired private WebhookEndpointRepository webhookEndpointRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcClient jdbcClient;

  @AfterEach
  void cleanDatabase() {
    jdbcClient
        .sql(
            """
                TRUNCATE TABLE deliveries, webhook_endpoints
                RESTART IDENTITY CASCADE
                """)
        .update();
  }

  @Test
  void concurrentWorkerSkipsLockedDelivery() throws Exception {
    WebhookEndpoint endpoint =
        webhookEndpointRepository.create(
            "concurrency-test", "https://example.com/webhooks/concurrency");

    Delivery firstPending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                        {"event":"first"}
                        """);

    Delivery secondPending =
        deliveryRepository.createPending(
            endpoint.id(),
            """
                        {"event":"second"}
                        """);

    CountDownLatch firstClaimed = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);

    AtomicLong firstClaimedId = new AtomicLong();

    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Delivery> firstWorker =
          executor.submit(
              () -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);

                return transaction.execute(
                    status -> {
                      Delivery claimed =
                          deliveryRepository.claimNextReady(Duration.ofSeconds(30)).orElseThrow();

                      firstClaimedId.set(claimed.id());
                      firstClaimed.countDown();
                      await(releaseFirst);

                      return claimed;
                    });
              });

      assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();

      Future<Delivery> secondWorker =
          executor.submit(
              () -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);

                return transaction.execute(
                    status ->
                        deliveryRepository.claimNextReady(Duration.ofSeconds(30)).orElseThrow());
              });

      Delivery secondResult = secondWorker.get(3, TimeUnit.SECONDS);

      assertThat(secondResult.id()).isNotEqualTo(firstClaimedId.get());

      releaseFirst.countDown();

      Delivery firstResult = firstWorker.get(5, TimeUnit.SECONDS);

      assertThat(firstResult.id()).isEqualTo(firstClaimedId.get());

      assertThat(firstResult.id()).isNotEqualTo(secondResult.id());

      assertThat(List.of(firstResult.id(), secondResult.id()))
          .containsExactlyInAnyOrder(firstPending.id(), secondPending.id());

    } finally {
      releaseFirst.countDown();
      executor.shutdown();
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Executor did not terminate");
      }
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for latch");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
