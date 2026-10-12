# common — Asynchronous Decoupling (MQ)

## Overview

Topic: `package-status-topic`

Package status updates move from latency-driven RPC to bandwidth-driven messaging.

Part of the [LogisticsConnect](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own.

- Producer: `delay-stage-service` (`../delay-stage-service`)
- Consumer(s): `transit-service` (required, stage 3); `alertbot` (stretch, stage 4 —
  reacts to stage changes to decide when to raise an alert)

Broker URL and topic name are shared via a common `co.wethinkcode.logisticsconnect.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has
`src/main/java/co/wethinkcode/logisticsconnect/mq/MqConfig.java`).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

`delay-stage-service` publishes JSON text events containing `hubId`, `stage`, and
`timestamp` when a stage changes. `transit-service` subscribes and updates an in-memory
latest-stage cache. If the broker is unavailable, transit retries its subscription;
delay-stage returns HTTP 503 for a changed stage it cannot publish, without storing
that unannounced update. An unchanged stage does not require a publish.

To verify end-to-end, start the broker, then start `transit-service` before
`delay-stage-service`. POST a new stage to `http://localhost:7052/delay-stage/H-500`,
then request `http://localhost:7053/transit/H-500`; the returned `delayStage` should
reflect the published event. The publisher and subscriber log connection and failure
information. The transit cache is in-memory, so it starts at stage 0 until it receives
an event after startup.

## Remaining optional work

- `alertbot` (stretch goal) can add its own subscriber logic and simulate an alert
  when a hub crosses a chosen delay-stage threshold.
