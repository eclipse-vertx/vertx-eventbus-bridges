module io.vertx.eventbusbridge.grpc {

  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  requires io.vertx.core;
  requires io.vertx.core.logging;
  requires io.vertx.eventbusbridge.common;
  requires io.vertx.grpc.server;
  requires io.vertx.grpc.common;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
  requires io.vertx.grpc.client;
    requires io.vertx.docgen;

    exports io.vertx.eventbus.bridge.grpc;
  exports io.vertx.grpc.event.v1alpha;
}
