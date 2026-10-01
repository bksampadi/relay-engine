package dev.bksampadi.relay.webhook;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebhookClient {

  private final RestClient restClient;

  public WebhookClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  public void send(String url, String payload) {
    restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity();
  }
}
