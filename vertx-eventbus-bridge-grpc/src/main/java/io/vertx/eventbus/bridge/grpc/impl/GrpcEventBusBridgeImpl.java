package io.vertx.eventbus.bridge.grpc.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.GrpcBridgeOptions;
import io.vertx.eventbus.bridge.grpc.GrpcEventBusBridge;
import io.vertx.eventbus.bridge.grpc.impl.handler.*;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.event.v1alpha.EventBusBridgeProto;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.ServiceMethodInvoker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The service implements the gRPC service defined in the protobuf file and exposes the EventBus operations through gRPC methods.
 */
public class GrpcEventBusBridgeImpl implements GrpcEventBusBridge {

  private static final ServiceName SERVICE_NAME = ServiceName.create("vertx.event.v1alpha.EventBusBridge");
  private static final Descriptors.ServiceDescriptor SERVICE_DESCRIPTOR = EventBusBridgeProto.getDescriptor().findServiceByName("EventBusBridge");

  private final EventBus eventBus;
  private final GrpcBridgeOptions options;
  private final Handler<BridgeEvent> bridgeEventHandler;
  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private final ReplyManager replies;
  private final EventBusBridgePublishHandler publisHandler;
  private final EventBusBridgeSendHandler sendHandler;
  private final EventBusBridgeRequestHandler requestHandler;
  private final EventBusBridgeSubscribeHandler subscribeHandler;
  private final EventBusBridgeUnsubscribeHandler unsubscribeHandler;
  private final EventBusBridgePingHandler pingHandler;

  public GrpcEventBusBridgeImpl(EventBus eventBus, GrpcBridgeOptions options, Handler<BridgeEvent> bridgeEventHandler) {
    this.eventBus = eventBus;
    this.options = options;
    this.bridgeEventHandler = bridgeEventHandler;
    this.replies = new ReplyManager(options.getReplyTimeout().toMillis());
    this.publisHandler = new EventBusBridgePublishHandler(eventBus, options, bridgeEventHandler, replies, compiledREs);
    this.sendHandler = new EventBusBridgeSendHandler(eventBus, options, bridgeEventHandler, replies, compiledREs);
    this.requestHandler = new EventBusBridgeRequestHandler(eventBus, options, bridgeEventHandler, replies, compiledREs);
    this.subscribeHandler = new EventBusBridgeSubscribeHandler(eventBus, options, bridgeEventHandler, replies, compiledREs);
    this.unsubscribeHandler = new EventBusBridgeUnsubscribeHandler(eventBus, options, bridgeEventHandler, replies, compiledREs, subscribeHandler);
    this.pingHandler = new EventBusBridgePingHandler(eventBus, options, bridgeEventHandler, replies, compiledREs);
  }

  @Override
  public ServiceName name() {
    return SERVICE_NAME;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return SERVICE_DESCRIPTOR;
  }

  @Override
  public List<ServiceMethod<?, ?>> methods() {
    return List.of(
      EventBusBridgePublishHandler.SERVICE_METHOD,
      EventBusBridgeSendHandler.SERVICE_METHOD,
      EventBusBridgeRequestHandler.SERVICE_METHOD,
      EventBusBridgeSubscribeHandler.SERVICE_METHOD,
      EventBusBridgeUnsubscribeHandler.SERVICE_METHOD,
      EventBusBridgePingHandler.SERVICE_METHOD
    );
  }

  @Override
  public <Req, Resp> ServiceMethodInvoker invoker(ServiceMethod<Req, Resp> method) {
    if (method.equals(EventBusBridgePublishHandler.SERVICE_METHOD)) {
      return publisHandler;
    } else if (method.equals(EventBusBridgeSendHandler.SERVICE_METHOD)) {
      return sendHandler;
    } else if (method.equals(EventBusBridgeRequestHandler.SERVICE_METHOD)) {
      return requestHandler;
    } else if (method.equals(EventBusBridgeSubscribeHandler.SERVICE_METHOD)) {
      return subscribeHandler;
    } else if (method.equals(EventBusBridgeUnsubscribeHandler.SERVICE_METHOD)) {
      return unsubscribeHandler;
    } else if (method.equals(EventBusBridgePingHandler.SERVICE_METHOD)) {
      return pingHandler;
    } else {
      return null;
    }
  }
}
