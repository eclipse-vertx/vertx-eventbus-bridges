package io.vertx.eventbus.bridge.grpc.impl.handler;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.*;
import io.vertx.grpc.event.v1alpha.EventMessage;
import io.vertx.grpc.event.v1alpha.SendMessageRequest;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.Map;
import java.util.regex.Pattern;

public class EventBusBridgeSendHandler extends EventBusBridgeHandlerBase<SendMessageRequest, EventMessage> {

  public static final ServiceMethod<SendMessageRequest, EventMessage> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Send",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(SendMessageRequest.newBuilder()));

  public EventBusBridgeSendHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler, Map<String, Pattern> compiledREs) {
    super(bus, options, bridgeEventHandler, compiledREs);
  }

  @Override
  public void handle(GrpcServerRequest<SendMessageRequest, EventMessage> request) {
    request.handler(eventRequest -> {
      String address = eventRequest.getAddress();
      if (address.isEmpty()) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid address");
        return;
      }

      JsonObject body = protoToJson(eventRequest.getBody());
      JsonObject eventJson = createEvent("send", eventRequest);

      if (!checkMatches(true, address)) {
        replyStatus(request, GrpcStatus.PERMISSION_DENIED);
        return;
      }

      checkCallHook(BridgeEventType.SEND, eventJson,
        () -> {
          DeliveryOptions deliveryOptions = createDeliveryOptions(eventRequest.getHeadersMap());

          if (!eventRequest.getReplyAddress().isEmpty()) {
            bus.request(address, body, deliveryOptions)
              .onSuccess(reply -> {
                if (reply.replyAddress() != null) {
                  replies.put(reply.replyAddress(), reply);
                }

                request.response().end(EventMessage.getDefaultInstance());
              })
              .onFailure(err -> {
                EventMessage response = handleErrorAndCreateResponse(err);
                request.response().end(response);
              });
          } else {
            bus.send(address, body, deliveryOptions);
            request.response().end(EventMessage.getDefaultInstance());
          }
        },
        () -> replyStatus(request, GrpcStatus.PERMISSION_DENIED));
    });
  }

  @Override
  protected JsonObject createEvent(String type, SendMessageRequest request) {
    JsonObject event = new JsonObject().put("type", type);

    if (request == null) {
      return event;
    }

    // Add address if present
    if (!request.getAddress().isEmpty()) {
      event.put("address", request.getAddress());
    }

    // Add reply address if present
    if (!request.getReplyAddress().isEmpty()) {
      event.put("replyAddress", request.getReplyAddress());
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
