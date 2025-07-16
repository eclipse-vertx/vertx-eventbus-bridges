package io.vertx.eventbus.bridge.grpc;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.eventbus.bridge.grpc.impl.GrpcEventBusBridgeServiceImpl;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.server.Service;

@VertxGen
public interface GrpcEventBusBridgeService extends Service {

  /**
   * Creates a new gRPC EventBus bridge service with default options and null bridge event handler.
   *
   * @param eventBus the Vert.x event bus instance to use
   * @return a new instance of GrpcEventBusBridgeService
   */
  static GrpcEventBusBridgeService create(EventBus eventBus) {
    return create(eventBus, null, null);
  }

  /**
   * Creates a new gRPC EventBus bridge service with the specified event bus and bridge options.
   *
   * @param eventBus the Vert.x event bus instance to use
   * @param options the bridge options for controlling access to the EventBus
   * @return a new instance of GrpcEventBusBridgeService
   */
  static GrpcEventBusBridgeService create(EventBus eventBus, GrpcBridgeOptions options) {
    return create(eventBus, options, null);
  }

  /**
   * Creates a new gRPC EventBus bridge service with the specified bridge options.
   *
   * @param eventBus the Vert.x event bus instance to use
   * @param options the bridge options for controlling access to the EventBus
   * @param bridgeEventHandler a handler for bridge events that can be used to implement custom security logic
   * @return a new instance of GrpcEventBusBridgeService
   */
  static GrpcEventBusBridgeService create(EventBus eventBus, GrpcBridgeOptions options, Handler<BridgeEvent> bridgeEventHandler) {
    return new GrpcEventBusBridgeServiceImpl(eventBus, options, bridgeEventHandler);
  }
}
