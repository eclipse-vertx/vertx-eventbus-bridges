package io.vertx.eventbus.bridge.grpc.impl.handler;

import com.google.protobuf.Empty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.eventbus.bridge.grpc.impl.ReplyManager;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.Map;
import java.util.regex.Pattern;

public class EventBusBridgePingHandler extends EventBusBridgeHandlerBase<Empty, Empty> {

  public static final ServiceMethod<Empty, Empty> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Ping",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(Empty.newBuilder()));

  public EventBusBridgePingHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler,
                                   ReplyManager replies, Map<String, Pattern> compiledREs) {
    super(bus, options, bridgeEventHandler, replies, compiledREs);
  }

  @Override
  public void handle(GrpcServerRequest<Empty, Empty> event) {
    JsonObject eventJson = createEvent("ping", null);

    checkCallHook(BridgeEventType.SOCKET_PING, eventJson,
      () -> event.response().send(Empty.getDefaultInstance()),
      () -> event.response().send(Empty.getDefaultInstance())
    );
  }

  @Override
  protected JsonObject createEvent(String type, Empty request) {
    return new JsonObject().put("type", type);
  }
}
