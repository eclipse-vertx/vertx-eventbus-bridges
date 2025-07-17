# vertx-eventbus-bridges

[![Build Status (5.x)](https://github.com/eclipse-vertx/vertx-eventbus-bridges/actions/workflows/ci-5.x.yml/badge.svg)](https://github.com/eclipse-vertx/vertx-eventbus-bridges/actions/workflows/ci-5.x.yml)

Event bus bridges.

## Event Bus Bridges

Event bus bridges are components that manage a (generally bidirectional) mapping between the vert.x event bus and another protocol:

```
    Event Bus <---- BRIDGE ----> gRPC / TCP / SOCKJS ...
```
