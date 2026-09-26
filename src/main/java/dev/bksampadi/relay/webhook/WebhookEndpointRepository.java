package dev.bksampadi.relay.webhook;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class WebhookEndpointRepository {

    private final JdbcClient jdbcClient;

    public WebhookEndpointRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public WebhookEndpoint create(String name, String url) {
        return jdbcClient.sql("""
                INSERT INTO webhook_endpoints (name, url)
                VALUES (:name, :url)
                RETURNING id, name, url, created_at
                """)
                .param("name", name)
                .param("url", url)
                .query(WebhookEndpointRepository::mapRow)
                .single();
    }

    public Optional<WebhookEndpoint> findById(long id) {
        return jdbcClient.sql("""
                SELECT id, name, url, created_at
                FROM webhook_endpoints
                WHERE id = :id
                """)
                .param("id", id)
                .query(WebhookEndpointRepository::mapRow)
                .optional();
    }

    private static WebhookEndpoint mapRow(ResultSet resultSet, int rowNum)
            throws SQLException {

        return new WebhookEndpoint(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("url"),
                resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                ).toInstant()
        );
    }
}