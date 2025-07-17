package io.vertx.eventbus.bridge.grpc;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;

/**
 * A builder for gRPC EventBus Bridge
 */
@VertxGen
public interface GrpcEventBusBridgeBuilder {

  /**
   * Override the default bridge options.
   *
   * @param options the options
   * @return this builder ref
   */
  GrpcEventBusBridgeBuilder with(GrpcBridgeOptions options);

  /**
   * Set a handler for bridge events that can be used to implement custom security logic.
   *
   * @param eventHandler the handler
   * @return this builder ref
   */
  GrpcEventBusBridgeBuilder withEventHandler(Handler<BridgeEvent> eventHandler);

  /**
   * Build and return the bridge instance, ready to be mounted in a Vert.x gRPC Server.
   *
   * @return the bridge
   */
  GrpcEventBusBridge build();

}
