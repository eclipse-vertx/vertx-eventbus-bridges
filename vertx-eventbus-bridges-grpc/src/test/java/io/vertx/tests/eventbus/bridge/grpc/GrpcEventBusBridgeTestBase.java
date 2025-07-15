package io.vertx.tests.eventbus.bridge.grpc;

import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.eventbus.bridge.grpc.BridgeEvent;
import io.vertx.eventbus.bridge.grpc.GrpcEventBusBridge;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.grpc.event.v1alpha.JsonPayload;
import io.vertx.grpc.event.v1alpha.JsonPayloadType;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public abstract class GrpcEventBusBridgeTestBase {

  protected Vertx vertx;
  protected GrpcEventBusBridge bridge;
  protected volatile Handler<BridgeEvent> eventHandler = event -> event.complete(true);

  /**
   * Convert a JsonObject to a JsonPayload
   */
  public static JsonPayload jsonToPayload(JsonObject json) {
    if (json == null) {
      return JsonPayload.newBuilder().build();
    }

    Value.Builder valueBuilder = Value.newBuilder();

    try {
      JsonFormat.parser().merge(json.encode(), valueBuilder);
    } catch (Exception e) {
      // If parsing fails, fallback to empty value
      valueBuilder.clear();
    }

    return JsonPayload.newBuilder()
      .setProtoBody(valueBuilder.build())
      .build();
  }

  public static JsonObject valueToJson(JsonPayload value) {
    return valueToJson(value, JsonPayloadType.proto);
  }

  /**
   * Convert a Protobuf Value to a JsonObject
   */
  public static JsonObject valueToJson(JsonPayload value, JsonPayloadType bodyType) {
    if (value == null) {
      return new JsonObject();
    }
    
    switch (bodyType) {
      case proto:
        JsonObject json = new JsonObject();
        try {
          String jsonString = JsonFormat.printer().print(value.getProtoBody());
          json = new JsonObject(jsonString);
        } catch (Exception e) {
          // If parsing fails, return empty object
        }
        return json;
      case binary:
        return new JsonObject(Buffer.buffer(value.getBinaryBody().toByteArray()));
      case text:
        return new JsonObject(value.getTextBody());
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
      new BridgeOptions()
        .addInboundPermitted(new PermittedOptions().setAddress("hello"))
        .addInboundPermitted(new PermittedOptions().setAddress("echo"))
        .addInboundPermitted(new PermittedOptions().setAddress("test"))
        .addInboundPermitted(new PermittedOptions().setAddress("complex-ping"))
        .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
        .addOutboundPermitted(new PermittedOptions().setAddress("test"))
        .addOutboundPermitted(new PermittedOptions().setAddress("ping"))
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
