# DelayStageServiceApp

## Overview

Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

MQ: on a valid stage change, this service publishes a JSON text message containing
`hubId`, `stage`, and `timestamp` to `package-status-topic` — see [`../common/`](../common).
If ActiveMQ cannot accept a changed-stage event, the endpoint returns HTTP 503 and
does not store the new stage. Reposting after broker recovery will therefore still
publish the change.

## Project structure

```
delay-stage-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── DelayStageServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/delay-stage-service.jar
```

Listens on port `7052`.

## Test

From this module directory, run `mvn clean test`. Unit and HTTP endpoint tests run
without a broker by injecting a test publisher. To test actual publishing, start the
broker using `../common/docker-compose.yml` and run the service.
