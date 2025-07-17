package io.vertx.eventbus.bridge.grpc.impl.handler;

import com.google.protobuf.Empty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.eventbus.bridge.grpc.impl.ReplyManager;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.*;
import io.vertx.grpc.event.v1alpha.SendOp;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.Map;
import java.util.regex.Pattern;

public class EventBusBridgeSendHandler extends EventBusBridgeHandlerBase<SendOp, Empty> {

  public static final ServiceMethod<SendOp, Empty> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Send",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(SendOp.newBuilder()));

  public EventBusBridgeSendHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler,
                                   ReplyManager replies, Map<String, Pattern> compiledREs) {
    super(bus, options, bridgeEventHandler, replies, compiledREs);
  }

  @Override
  public void handle(GrpcServerRequest<SendOp, Empty> request) {
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
          if (!replies.tryReply(address, body, deliveryOptions)) {
            MessageProducer<Object> sender = bus.sender(address, deliveryOptions);
            sender.write(body).onComplete(ar -> {
              if (ar.succeeded()) {
                request.response().end(Empty.getDefaultInstance());
              } else if (ar.cause() instanceof ReplyException) {
                ReplyException replyException = (ReplyException) ar.cause();
                switch (replyException.failureType()) {
                  case NO_HANDLERS:
                    replyStatus(request, GrpcStatus.NOT_FOUND);
                    break;
                  default:
                    request.response().end(Empty.getDefaultInstance());
                    break;
                }
              }
            });
          } else {
            request.response().end(Empty.getDefaultInstance());
          }
        },
        () -> replyStatus(request, GrpcStatus.PERMISSION_DENIED));
    });
  }

  @Override
  protected JsonObject createEvent(String type, SendOp request) {
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
