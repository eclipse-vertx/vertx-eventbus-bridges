package io.vertx.eventbus.bridge.grpc.impl.handler;

import com.google.protobuf.Empty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.*;
import io.vertx.grpc.event.v1alpha.PublishOp;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.Map;
import java.util.regex.Pattern;

public class EventBusBridgePublishHandler extends EventBusBridgeHandlerBase<PublishOp, Empty> {

  public static final ServiceMethod<PublishOp, Empty> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Publish",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(PublishOp.newBuilder()));

  public EventBusBridgePublishHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler, Map<String, Pattern> compiledREs) {
    super(bus, options, bridgeEventHandler, compiledREs);
  }

  @Override
  public void handle(GrpcServerRequest<PublishOp, Empty> request) {
    request.handler(eventRequest -> {
      String address = eventRequest.getAddress();
      if (address.isEmpty()) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid address");
        return;
      }

      Object body = protoToJson(eventRequest.getBody());
      JsonObject eventJson = createEvent("publish", eventRequest);

      if (!checkMatches(true, address)) {
        replyStatus(request, GrpcStatus.PERMISSION_DENIED);
        return;
      }

      checkCallHook(BridgeEventType.PUBLISH, eventJson,
        () -> {
          DeliveryOptions deliveryOptions = createDeliveryOptions(eventRequest.getHeadersMap());
          bus.publish(address, body, deliveryOptions);
          request.response().end(Empty.getDefaultInstance());
        },
        () -> replyStatus(request, GrpcStatus.PERMISSION_DENIED));
    });
  }

  @Override
  protected JsonObject createEvent(String type, PublishOp request) {
    JsonObject event = new JsonObject().put("type", type);

    if (request == null) {
      return event;
    }

    // Add address if present
    if (!request.getAddress().isEmpty()) {
      event.put("address", request.getAddress());
    }

    // Add headers if present
    if (!request.getHeadersMap().isEmpty()) {
      JsonObject headers = new JsonObject();
      request.getHeadersMap().forEach(headers::put);
      event.put("headers", headers);
    }

    return event;
  }
}
