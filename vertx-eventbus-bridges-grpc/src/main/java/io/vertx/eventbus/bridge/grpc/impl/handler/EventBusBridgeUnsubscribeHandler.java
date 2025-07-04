package io.vertx.eventbus.bridge.grpc.impl.handler;

import com.google.protobuf.Empty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.*;
import io.vertx.grpc.event.v1alpha.UnsubscribeMessageRequest;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.Map;
import java.util.regex.Pattern;

public class EventBusBridgeUnsubscribeHandler extends EventBusBridgeHandlerBase<UnsubscribeMessageRequest, Empty> {

  public static final ServiceMethod<UnsubscribeMessageRequest, Empty> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Unsubscribe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(UnsubscribeMessageRequest.newBuilder()));

  public EventBusBridgeUnsubscribeHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler, Map<String, Pattern> compiledREs) {
    super(bus, options, bridgeEventHandler, compiledREs);
  }

  @Override
  public void handle(GrpcServerRequest<UnsubscribeMessageRequest, Empty> request) {
    request.handler(eventRequest -> {
      String address = eventRequest.getAddress();
      String consumerId = eventRequest.getConsumer();

      if (address.isEmpty()) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid address");
        return;
      }

      if (consumerId.isEmpty()) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid consumer id");
        return;
      }

      JsonObject eventJson = createEvent("unregister", eventRequest);

      if (!checkMatches(false, address)) {
        replyStatus(request, GrpcStatus.PERMISSION_DENIED);
        return;
      }

      checkCallHook(BridgeEventType.UNREGISTER, eventJson,
        () -> {
          if (unregisterConsumer(address, consumerId)) {
            request.response().end(Empty.getDefaultInstance());
          } else {
            request.response().status(GrpcStatus.NOT_FOUND).end();
          }
        },
        () -> replyStatus(request, GrpcStatus.PERMISSION_DENIED));
    });
  }

  @Override
  protected JsonObject createEvent(String type, UnsubscribeMessageRequest request) {
    JsonObject event = new JsonObject().put("type", type);

    if (request == null) {
      return event;
    }

    // Add address if present
    if (!request.getAddress().isEmpty()) {
      event.put("address", request.getAddress());
    }

    // Add consumer ID if present
    if (!request.getConsumer().isEmpty()) {
      event.put("consumer", request.getConsumer());
    }

    return event;
  }
}
