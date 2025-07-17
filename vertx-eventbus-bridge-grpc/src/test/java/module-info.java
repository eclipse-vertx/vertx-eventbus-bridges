open module io.vertx.tests.eventbusbridge.grpc {
  requires io.vertx.eventbusbridge.common;
  requires io.vertx.eventbusbridge.grpc;
  requires io.vertx.grpc.client;
  requires junit;
  requires assertj.core;
  requires io.vertx.testing.unit;
  requires io.vertx.grpc.server;
  requires io.vertx.grpc.common;
  requires io.vertx.core;
  requires com.google.protobuf;
  requires com.google.protobuf.util;
}
