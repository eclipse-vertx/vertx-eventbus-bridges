package io.vertx.eventbus.bridge.grpc.impl.handler;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.impl.EventBusBridgeHandlerBase;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.*;
import io.vertx.grpc.event.v1alpha.EventBusMessage;
import io.vertx.grpc.event.v1alpha.JsonValue;
import io.vertx.grpc.event.v1alpha.JsonValueFormat;
import io.vertx.grpc.event.v1alpha.SubscribeOp;
import io.vertx.grpc.server.GrpcServerRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class EventBusBridgeSubscribeHandler extends EventBusBridgeHandlerBase<SubscribeOp, EventBusMessage> {

  public static final ServiceMethod<SubscribeOp, EventBusMessage> SERVICE_METHOD = ServiceMethod.server(
    ServiceName.create("vertx.event.v1alpha.EventBusBridge"),
    "Subscribe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(SubscribeOp.newBuilder()));

  public EventBusBridgeSubscribeHandler(EventBus bus, BridgeOptions options, Handler<BridgeEvent> bridgeEventHandler,
                                        Map<String, Pattern> compiledREs) {
    super(bus, options, bridgeEventHandler, compiledREs);
  }

  @Override
  public void handle(GrpcServerRequest<SubscribeOp, EventBusMessage> request) {
    request.handler(eventRequest -> {
      String address = eventRequest.getAddress();
      if (address.isEmpty()) {
        replyStatus(request, GrpcStatus.INVALID_ARGUMENT, "Invalid address");
        return;
      }

      JsonObject event = createEvent("register", eventRequest);

      if (!checkMatches(false, address)) {
        replyStatus(request, GrpcStatus.PERMISSION_DENIED);
        return;
      }

      checkCallHook(BridgeEventType.REGISTER, event,
        () -> {
          String consumerId = UUID.randomUUID().toString();
          requests.put(consumerId, request);

          MessageConsumer<Object> consumer = bus.consumer(address,
            new BridgeMessageConsumer(request, address, consumerId, eventRequest.getMessageBodyFormat()));

          Map<String, MessageConsumer<?>> addressConsumers = consumers.computeIfAbsent(address, k -> new ConcurrentHashMap<>());
          addressConsumers.put(consumerId, consumer);

          request.connection().closeHandler(v -> unregisterConsumer(address, consumerId));
        },
        () -> replyStatus(request, GrpcStatus.PERMISSION_DENIED));
    });
  }

  @Override
  protected JsonObject createEvent(String type, SubscribeOp request) {
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

    // Add headers if present
    if (!request.getHeadersMap().isEmpty()) {
      JsonObject headers = new JsonObject();
      request.getHeadersMap().forEach(headers::put);
      event.put("headers", headers);
    }

    return event;
  }

  static final class BridgeMessageConsumer implements Handler<Message<Object>> {
    private final GrpcServerRequest<SubscribeOp, EventBusMessage> request;
    private final String address;
    private final String consumerId;
    private final JsonValueFormat jsonValueFormat;

    BridgeMessageConsumer(GrpcServerRequest<SubscribeOp, EventBusMessage> request, String address,
                          String consumerId, JsonValueFormat jsonValueFormat) {
      this.request = request;
      this.address = address;
      this.consumerId = consumerId;
      this.jsonValueFormat = jsonValueFormat;
    }

    @Override
    public void handle(Message<Object> message) {
      Map<String, String> responseHeaders = new HashMap<>();
      for (Map.Entry<String, String> entry : message.headers()) {
        responseHeaders.put(entry.getKey(), entry.getValue());
      }

      JsonValue body;
      if (message.body() instanceof JsonObject) {
        body = jsonToProto((JsonObject) message.body(), jsonValueFormat);
      } else if (message.body() instanceof String) {
        body = jsonToProto(new JsonObject(String.valueOf(message.body())), jsonValueFormat);
      } else {
        body = jsonToProto(new JsonObject().put("value", String.valueOf(message.body())), jsonValueFormat);
      }

      EventBusMessage response = EventBusMessage.newBuilder()
        .setAddress(address)
        .setConsumer(consumerId)
        .putAllHeaders(responseHeaders)
        .setBody(body)
        .build();

      if (message.replyAddress() != null) {
        response = response.toBuilder().setReplyAddress(message.replyAddress()).build();
        replies.put(message.replyAddress(), message);
      }

      request.resume();
      request.response().write(response);
      request.pause();
    }
  }
}
