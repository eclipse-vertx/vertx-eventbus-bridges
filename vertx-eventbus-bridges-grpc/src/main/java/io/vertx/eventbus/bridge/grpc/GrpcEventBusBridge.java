package io.vertx.eventbus.bridge.grpc;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.eventbus.bridge.grpc.impl.GrpcEventBusBridgeImpl;
import io.vertx.grpc.server.Service;

import java.util.Objects;

/**
 * <p>gRPC EventBus bridge for Vert.x.</p>
 *
 * <p>This interface provides methods to create and manage a gRPC service that bridges the Vert.x EventBus to external clients
 * using the  gRPC protocol. It allows external applications to communicate with the Vert.x EventBus through gRPC,
 * enabling cross-platform and cross-language communication.</p>
 *
 * <p>The bridge supports operations like publishing messages, sending requests, subscribing to addresses, and handling
 * responses from the EventBus.</p>
 */
@VertxGen
public interface GrpcEventBusBridge extends Service {

  /**
   * Create a builder for advanced configuration of the bridge.
   *
   * @param vertx the vertx instance
   * @return the builder
   */
  static GrpcEventBusBridgeBuilder builder(Vertx vertx) {
    Objects.requireNonNull(vertx);
    return new GrpcEventBusBridgeBuilder() {
      private GrpcBridgeOptions options;
      private Handler<BridgeEvent> bridgeEventHandler;
      @Override
      public GrpcEventBusBridgeBuilder with(GrpcBridgeOptions options) {
        this.options = options;
        return this;
      }
      @Override
      public GrpcEventBusBridgeBuilder withEventHandler(Handler<BridgeEvent> eventHandler) {
        this.bridgeEventHandler = eventHandler;
        return this;
      }
      @Override
      public GrpcEventBusBridge build() {
        GrpcBridgeOptions options = this.options;
        if (options == null) {
          options = new GrpcBridgeOptions();
        }
        return new GrpcEventBusBridgeImpl(vertx.eventBus(), options, bridgeEventHandler);
      }
    };
  }

  /**
   * Creates a new gRPC EventBus bridge service with default options and null bridge event handler.
   *
   * @param vertx the Vert.x instance to use
   * @return a new instance of GrpcEventBusBridgeService
   */
  static GrpcEventBusBridge create(Vertx vertx) {
    return create(vertx, new GrpcBridgeOptions());
  }

  /**
   * Creates a new gRPC EventBus bridge service with the specified event bus and bridge options.
   *
   * @param vertx the Vert.x instance to use
   * @param options the bridge options for controlling access to the EventBus
   * @return a new instance of GrpcEventBusBridgeService
   */
  static GrpcEventBusBridge create(Vertx vertx, GrpcBridgeOptions options) {
    return new GrpcEventBusBridgeImpl(vertx.eventBus(), options, null);
  }
}
