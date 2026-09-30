package dev.bksampadi.relay.delivery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {

  private final JdbcClient jdbcClient;

  public DeliveryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public Delivery createPending(long webhookEndpointId, String payload) {
    return jdbcClient
        .sql(
            """
                        INSERT INTO deliveries (
                            webhook_endpoint_id,
                            payload,
                            status
                        )
                        VALUES (
                            :webhookEndpointId,
                            CAST(:payload AS jsonb),
                            'PENDING'
                        )
                        RETURNING
                            id,
                            webhook_endpoint_id,
                            payload,
                            status,
                            attempt_count,
                            next_attempt_at,
                            lease_until,
                            created_at,
                            updated_at
                        """)
        .param("webhookEndpointId", webhookEndpointId)
        .param("payload", payload)
        .query(DeliveryRepository::mapRow)
        .single();
  }

  public Optional<Delivery> findById(long id) {
    return jdbcClient
        .sql(
            """
                SELECT
                    id,
                    webhook_endpoint_id,
                    payload,
                    status,
                    attempt_count,
                    next_attempt_at,
                    lease_until,
                    created_at,
                    updated_at
                FROM deliveries
                WHERE id = :id
                """)
        .param("id", id)
        .query(DeliveryRepository::mapRow)
        .optional();
  }

  public Optional<Delivery> claimNextReady(Duration leaseDuration) {
    return jdbcClient
        .sql(
            """
                WITH next_delivery AS (
                    SELECT id
                    FROM deliveries
                    WHERE status = 'PENDING'
                        AND next_attempt_at <= NOW()
                    ORDER BY next_attempt_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE deliveries d
                SET status = 'PROCESSING',
                    attempt_count = d.attempt_count +1,
                    lease_until = NOW() + (:leaseSeconds * INTERVAL '1 second'),
                    updated_at = NOW()
                FROM next_delivery
                WHERE d.id = next_delivery.id
                RETURNING
                    d.id,
                    d.webhook_endpoint_id,
                    d.payload,
                    d.status,
                    d.attempt_count,
                    d.next_attempt_at,
                    d.lease_until,
                    d.created_at,
                    d.updated_at
                """)
        .param("leaseSeconds", leaseDuration.toSeconds())
        .query(DeliveryRepository::mapRow)
        .optional();
  }

  private static Delivery mapRow(ResultSet resultSet, int rowNum) throws SQLException {

    OffsetDateTime leaseUntil = resultSet.getObject("lease_until", OffsetDateTime.class);

    return new Delivery(
        resultSet.getLong("id"),
        resultSet.getLong("webhook_endpoint_id"),
        resultSet.getString("payload"),
        DeliveryStatus.valueOf(resultSet.getString("status")),
        resultSet.getInt("attempt_count"),
        resultSet.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
        leaseUntil == null ? null : leaseUntil.toInstant(),
        resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
        resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
  }
}
