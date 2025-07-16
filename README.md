# vertx-eventbus-bridges

Event bus bridges.

## Event Bus Bridges

Event bus bridges are components that manage a (generally bidirectional) mapping between the vert.x event bus and another protocol:

```
    Event Bus <---- BRIDGE ----> Stomp / AMQP / TCP / SOCKJS ...
```

To configure these bridges you need to configure:

* which event bus address is accepted by the bridge to be transferred to the third-party protocol.
* which address / url / topic from the third-protocol is transferred to the event bus
