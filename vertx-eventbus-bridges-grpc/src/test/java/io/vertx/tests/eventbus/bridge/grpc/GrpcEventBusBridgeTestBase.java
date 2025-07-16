package io.vertx.tests.eventbus.bridge.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.GrpcBridgeOptions;
import io.vertx.eventbus.bridge.grpc.GrpcEventBusBridge;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.event.v1alpha.JsonValue;
import io.vertx.grpc.event.v1alpha.JsonValueFormat;
import junit.framework.AssertionFailedError;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@RunWith(VertxUnitRunner.class)
public abstract class GrpcEventBusBridgeTestBase {

  protected Vertx vertx;
  protected GrpcEventBusBridge bridge;
  protected volatile Handler<BridgeEvent> eventHandler = event -> event.complete(true);

  /**
   * Convert a Json value to a JsonPayload
   */
  public static JsonValue jsonToPayload(Object json) {
    return jsonToPayload(json , JsonValueFormat.proto);
  }

  /**
   * Convert a JsonObject to a JsonPayload
   */
  public static JsonValue jsonToPayload(Object json, JsonValueFormat type) {
    switch (type) {
      case proto:
        Value.Builder valueBuilder = Value.newBuilder();
        try {
          JsonFormat.parser().merge(Json.encode(json), valueBuilder);
        } catch (Exception e) {
          AssertionFailedError afe = new AssertionFailedError();
          afe.initCause(e);
          throw afe;
        }
        return JsonValue.newBuilder()
          .setProto(valueBuilder.build())
          .build();
      case binary:
        return JsonValue.newBuilder()
          .setBinary(ByteString.copyFrom(Json.encodeToBuffer(json).getBytes()))
          .build();
      case text:
        return JsonValue.newBuilder()
          .setText(Json.encode(json))
          .build();
      default:
        throw new UnsupportedOperationException();
    }

  }

  public static <T> T valueToJson(JsonValue value) {
    return valueToJson(value, JsonValueFormat.proto);
  }

  /**
   * Convert a Protobuf Value to a JsonObject
   */
  public static <T> T valueToJson(JsonValue value, JsonValueFormat bodyType) {

    switch (bodyType) {
      case proto:
        try {
          String jsonString = JsonFormat.printer().print(value.getProto());
          return (T) Json.decodeValue(jsonString);
        } catch (Exception e) {
          AssertionFailedError afe = new AssertionFailedError();
          afe.initCause(e);
          throw afe;
        }
      case binary:
        return (T) Json.decodeValue(Buffer.buffer(value.getBinary().toByteArray()));
      case text:
        return (T) Json.decodeValue(value.getText());
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Before
  public void before() {
    vertx = Vertx.vertx();

    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));
    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));
    vertx.setPeriodic(1000, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));

    bridge = GrpcEventBusBridge.create(
      vertx,
      new GrpcBridgeOptions()
        .setReplyTimeout(Duration.of(3, ChronoUnit.SECONDS))
        .addInboundPermitted(new PermittedOptions().setAddress("hello"))
        .addInboundPermitted(new PermittedOptions().setAddress("echo"))
        .addInboundPermitted(new PermittedOptions().setAddress("test"))
        .addInboundPermitted(new PermittedOptions().setAddress("complex-ping"))
        .addInboundPermitted(new PermittedOptions().setAddress("the-address"))
        .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
        .addOutboundPermitted(new PermittedOptions().setAddress("test"))
        .addOutboundPermitted(new PermittedOptions().setAddress("ping"))
        .addOutboundPermitted(new PermittedOptions().setAddress("the-address"))
        .addOutboundPermitted(new PermittedOptions().setAddress("complex-ping")),
      7000,
      event -> eventHandler.handle(event));

    bridge.listen().await();
  }

  @After
  public void after(TestContext context) {
    Async async = context.async();
    if (bridge != null) {
      bridge.close().onComplete(v -> vertx.close().onComplete(context.asyncAssertSuccess(h -> async.complete())));
    } else {
      vertx.close().onComplete(context.asyncAssertSuccess(h -> async.complete()));
    }
  }
}
