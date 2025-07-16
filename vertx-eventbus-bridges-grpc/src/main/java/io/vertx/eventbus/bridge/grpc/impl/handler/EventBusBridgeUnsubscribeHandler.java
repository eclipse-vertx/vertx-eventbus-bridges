package io.vertx.eventbus.bridge.grpc.impl.handler;

import com.google.protobuf.Empty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.common.*;
import io.vertx.grpc.event.v1alpha.UnsubscribeOp;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.Map;
import java.util.regex.Pattern;

public class EventBusBridgeUnsubscribeHandler extends EventBusBridgeHandlerBase<UnsubscribeOp, Empty> {

  public static final ServiceMethod<UnsubscribeOp, Empty> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Unsubscribe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(UnsubscribeOp.newBuilder()));

  private final EventBusBridgeSubscribeHandler subscribeHandler;

  public EventBusBridgeUnsubscribeHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler,
                                          Map<String, Pattern> compiledREs, EventBusBridgeSubscribeHandler subscribeHandler) {
    super(bus, options, bridgeEventHandler, compiledREs);

    this.subscribeHandler = subscribeHandler;
  }

  @Override
  public void handle(GrpcServerRequest<UnsubscribeOp, Empty> request) {
    request.handler(eventRequest -> {
      String consumerId = eventRequest.getConsumerId();

      if (consumerId.isEmpty()) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid consumer id");
        return;
      }

      JsonObject eventJson = createEvent("unregister", eventRequest);

      checkCallHook(BridgeEventType.UNREGISTER, eventJson,
        () -> {
          GrpcServerRequest<?, ?> r;
          if ((r = subscribeHandler.unregisterConsumer(consumerId)) != null) {
            r.response().end();
            request.response().end(Empty.getDefaultInstance());
          } else {
            request.response().status(GrpcStatus.NOT_FOUND).end();
          }
        },
        () -> replyStatus(request, GrpcStatus.PERMISSION_DENIED));
    });
  }

  @Override
  protected JsonObject createEvent(String type, UnsubscribeOp request) {
    JsonObject event = new JsonObject().put("type", type);

    if (request == null) {
      return event;
    }

    // Add consumer ID if present
    if (!request.getConsumerId().isEmpty()) {
      event.put("consumer", request.getConsumerId());
    }

    return event;
  }
}
