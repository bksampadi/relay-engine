package dev.bksampadi.relay.delivery;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class DeliveryRepository {

    private final JdbcClient jdbcClient;
    public DeliveryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Delivery createPending(long webhookEndpointId, String payload) {
        return jdbcClient.sql("""
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
        return jdbcClient.sql("""
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

    private static Delivery mapRow(ResultSet resultSet, int rowNum)
            throws SQLException {

        OffsetDateTime leaseUntil =
                resultSet.getObject("lease_until", OffsetDateTime.class);

        return new Delivery(
                resultSet.getLong("id"),
                resultSet.getLong("webhook_endpoint_id"),
                resultSet.getString("payload"),
                DeliveryStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                resultSet.getObject(
                        "next_attempt_at",
                        OffsetDateTime.class
                ).toInstant(),
                leaseUntil == null ? null : leaseUntil.toInstant(),
                resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                ).toInstant(),
                resultSet.getObject(
                        "updated_at",
                        OffsetDateTime.class
                ).toInstant()
        );
    }
}