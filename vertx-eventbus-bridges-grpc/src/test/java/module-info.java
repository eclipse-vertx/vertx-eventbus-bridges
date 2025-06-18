open module io.vertx.tests.eventbusbridge.grpc {
    requires io.vertx.core;
    requires io.vertx.eventbusbridge.common;
    requires io.vertx.eventbusbridge.grpc;

    requires com.google.protobuf;
    requires com.google.protobuf.util;
    requires io.vertx.grpc.client;
    requires io.vertx.grpc.common;

    requires junit;
    requires assertj.core;
    requires io.vertx.testing.unit;
}
