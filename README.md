# Relay Engine

![Java 21](https://img.shields.io/badge/Java-21-596675?style=flat-square)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-74866F?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-5F7390?style=flat-square)
![CI](https://img.shields.io/github/actions/workflow/status/bksampadi/relay-engine/ci.yml?branch=main&style=flat-square&label=CI)

Durable webhook delivery in Java and Spring Boot.

**Claim safely. Deliver over HTTP. Retry durably.**


```mermaid
flowchart LR
    DB[(PostgreSQL)]
    Claim[DeliveryClaimService]
    Processor[DeliveryProcessor]
    Client[WebhookClient]
    Endpoint[Webhook Endpoint]

    DB -->|PENDING| Claim
    Claim -->|PROCESSING + lease| DB
    Claim --> Processor
    Processor --> Client
    Client -->|HTTP POST| Endpoint
    Endpoint -->|2xx| Processor
    Processor -->|SUCCEEDED| DB
    Endpoint -->|failure| Processor
    Processor -->|PENDING + 30s| DB

    classDef persistence fill:#5F7390,color:#ffffff,stroke:#485A70
    classDef service fill:#7D927F,color:#ffffff,stroke:#617263
    classDef transport fill:#7C8796,color:#ffffff,stroke:#606A76
    classDef external fill:#ECE9E3,color:#2F3437,stroke:#B8B2A8

    class DB persistence
    class Claim,Processor service
    class Client transport
    class Endpoint external
```

## Delivery state

```text
PENDING
   |
   | claim
   v
PROCESSING
   |
   +---- 2xx --------> SUCCEEDED
   |
   +---- failure ----> PENDING
                       retry after 30s
```

Claims use PostgreSQL row locking:

```sql
FOR UPDATE SKIP LOCKED
```

A claim increments `attempt_count` and creates a processing lease. Success and retry transitions are guarded so only `PROCESSING` deliveries can leave the processing state.

The claim transaction is committed before the outbound HTTP request:

```text
claim -> commit -> HTTP POST -> state update
```

No database transaction is held open while waiting on the remote endpoint.

## Stack

Java 21 · Spring Boot 4 · Spring JDBC · RestClient · PostgreSQL · Flyway · Testcontainers · JUnit · Maven

## Test

```bash
./mvnw test
```

Windows:

```bat
mvnw.cmd test
```

Tests cover PostgreSQL persistence, concurrent claiming, delivery state transitions, HTTP transport, successful delivery, and retry scheduling.

## Current scope

Implemented:

- PostgreSQL-backed delivery persistence
- transactional claiming and leases
- concurrent worker safety
- HTTP webhook delivery
- successful completion
- fixed-delay retry
- integration testing with Testcontainers

Next: lease recovery, retry limits, backoff, and worker scheduling.