package dev.bksampadi.relay;

import org.springframework.boot.SpringApplication;

public class TestRelayEngineApplication {

  public static void main(String[] args) {
    SpringApplication.from(RelayEngineApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
