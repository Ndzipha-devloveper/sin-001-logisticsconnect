# TransitServiceApp

## Overview

Calculates estimated arrival windows based on hub and delay stage.

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service subscribes to the ActiveMQ topic `package-status-topic` and keeps
the latest received stage per hub in a thread-safe in-memory cache. If the broker is
unavailable, the subscriber retries its connection every five seconds. The cache is
not persisted and is populated as messages arrive.

## Project structure

```
transit-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── TransitServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/transit-service.jar
```

Listens on port `7053`.

## Endpoint

`GET /transit/{hubId}` loads hub details from hub-service and calculates an ETA using
the cached MQ delay stage (24 baseline hours plus 3 hours per stage). The response
includes hub details, `delayStage`, `estimatedHours`, and an ISO-8601
`estimatedArrival`. Transit does not call delay-stage-service.

## Test

From this module directory, run `mvn clean test`. Tests use an injected hub lookup
and stage cache, so they do not require a running broker or hub-service.
