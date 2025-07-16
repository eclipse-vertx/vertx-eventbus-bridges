package io.vertx.eventbus.bridge.grpc.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.GrpcBridgeOptions;
import io.vertx.eventbus.bridge.grpc.GrpcEventBusBridgeService;
import io.vertx.eventbus.bridge.grpc.impl.handler.*;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.event.v1alpha.EventBusBridgeProto;
import io.vertx.grpc.server.GrpcServer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The service implements the gRPC service defined in the protobuf file and exposes the EventBus operations through gRPC methods.
 */
public class GrpcEventBusBridgeServiceImpl implements GrpcEventBusBridgeService {

  private static final ServiceName SERVICE_NAME = ServiceName.create("vertx.event.v1alpha.EventBusBridge");
  private static final Descriptors.ServiceDescriptor SERVICE_DESCRIPTOR = EventBusBridgeProto.getDescriptor().findServiceByName("EventBusBridge");

  private final EventBus eventBus;
  private final GrpcBridgeOptions options;
  private final Handler<BridgeEvent> bridgeEventHandler;
  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private final ReplyManager replies;

  public GrpcEventBusBridgeServiceImpl(EventBus eventBus, GrpcBridgeOptions options, Handler<BridgeEvent> bridgeEventHandler) {
    this.eventBus = eventBus;
    this.options = options;
    this.bridgeEventHandler = bridgeEventHandler;
    this.replies = new ReplyManager(options.getReplyTimeout().toMillis());
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
  public void bind(GrpcServer server) {
    EventBusBridgeSubscribeHandler a;
    // Register handlers for all supported operations
    server.callHandler(EventBusBridgePublishHandler.SERVICE_METHOD, new EventBusBridgePublishHandler(eventBus, options, bridgeEventHandler, replies, compiledREs));
    server.callHandler(EventBusBridgeSendHandler.SERVICE_METHOD, new EventBusBridgeSendHandler(eventBus, options, bridgeEventHandler, replies, compiledREs));
    server.callHandler(EventBusBridgeRequestHandler.SERVICE_METHOD, new EventBusBridgeRequestHandler(eventBus, options, bridgeEventHandler, replies, compiledREs));
    server.callHandler(EventBusBridgeSubscribeHandler.SERVICE_METHOD, a = new EventBusBridgeSubscribeHandler(eventBus, options, bridgeEventHandler, replies, compiledREs));
    server.callHandler(EventBusBridgeUnsubscribeHandler.SERVICE_METHOD, new EventBusBridgeUnsubscribeHandler(eventBus, options, bridgeEventHandler, replies, compiledREs, a));
    server.callHandler(EventBusBridgePingHandler.SERVICE_METHOD, new EventBusBridgePingHandler(eventBus, options, bridgeEventHandler, replies, compiledREs));
  }
}
