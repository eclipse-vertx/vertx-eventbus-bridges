package io.vertx.eventbus.bridge.grpc.impl.handler;

import com.google.protobuf.util.Durations;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.eventbus.bridge.grpc.impl.ReplyManager;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.*;
import io.vertx.grpc.event.v1alpha.EventBusMessage;
import io.vertx.grpc.event.v1alpha.JsonValue;
import io.vertx.grpc.event.v1alpha.JsonValueFormat;
import io.vertx.grpc.event.v1alpha.RequestOp;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class EventBusBridgeRequestHandler extends EventBusBridgeHandlerBase<RequestOp, EventBusMessage> {

  public static final ServiceMethod<RequestOp, EventBusMessage> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Request",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(RequestOp.newBuilder()));

  public EventBusBridgeRequestHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler,
                                      ReplyManager replies, Map<String, Pattern> compiledREs) {
    super(bus, options, bridgeEventHandler, replies, compiledREs);
  }

  @Override
  public void handle(GrpcServerRequest<RequestOp, EventBusMessage> request) {
    request.handler(eventRequest -> {
      String address = eventRequest.getAddress();
      if (address.isEmpty()) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid address");
        return;
      }

      Object body;
      try {
        body = protoToJson(eventRequest.getBody());
      } catch (Exception e) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid body");
        return;
      }

      JsonObject eventJson = createEvent("send", eventRequest);

      if (!checkMatches(true, address)) {
        replyStatus(request, GrpcStatus.PERMISSION_DENIED);
        return;
      }

      checkCallHook(BridgeEventType.SEND, eventJson,
        () -> {
          DeliveryOptions deliveryOptions = createDeliveryOptions(eventRequest.getHeadersMap());
          long timeout = Durations.toMillis(eventRequest.getTimeout());

          if (timeout > 0) {
            deliveryOptions.setSendTimeout(timeout);
          }

          bus.request(address, body, deliveryOptions)
            .onSuccess(reply -> {
              Map<String, String> responseHeaders = new HashMap<>();
              for (Map.Entry<String, String> entry : reply.headers()) {
                responseHeaders.put(entry.getKey(), entry.getValue());
              }

              JsonValueFormat replyBodyType = eventRequest.getReplyBodyFormat();
              JsonValue replyBody;

              if (reply.body() instanceof JsonObject) {
                replyBody = toJsonValue(reply.body(), replyBodyType);
              } else if (reply.body() instanceof String) {
                replyBody = toJsonValue(new JsonObject().put("value", reply.body()), replyBodyType);
              } else {
                replyBody = toJsonValue(new JsonObject().put("value", String.valueOf(reply.body())), replyBodyType);
              }

              EventBusMessage.Builder response = EventBusMessage.newBuilder()
                .putAllHeaders(responseHeaders)
                .setBody(replyBody);

              if (reply.replyAddress() != null) {
                response.setReplyAddress(replies.createReply(reply));
              }

              request.response().end(response.build());
            })
            .onFailure(err -> {
              EventBusMessage response = handleErrorAndCreateResponse(err);
              request.response().end(response);
            });
        },
        () -> replyStatus(request, GrpcStatus.PERMISSION_DENIED));
    });
  }

  @Override
  protected JsonObject createEvent(String type, RequestOp request) {
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
