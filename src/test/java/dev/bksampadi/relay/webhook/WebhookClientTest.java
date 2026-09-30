package dev.bksampadi.relay.webhook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientResponseException;

@RestClientTest(WebhookClient.class)
class WebhookClientTest {

  @Autowired private WebhookClient webhookClient;

  @Autowired private MockRestServiceServer server;

  @Test
  void sendsJsonPayload() {
    String url = "https://example.com/webhooks/payments";
    String payload =
        """
        {"event":"payment.completed","payment_id":"pay_123"}
        """;

    server
        .expect(requestTo(url))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(payload))
        .andRespond(withSuccess());

    webhookClient.send(url, payload);

    server.verify();
  }

  @Test
  void throwsWhenEndpointReturnsServerError() {
    String url = "https://example.com/webhooks/payments";

    server.expect(requestTo(url)).andRespond(withServerError());

    assertThatThrownBy(() -> webhookClient.send(url, "{}"))
        .isInstanceOf(RestClientResponseException.class);

    server.verify();
  }
}
