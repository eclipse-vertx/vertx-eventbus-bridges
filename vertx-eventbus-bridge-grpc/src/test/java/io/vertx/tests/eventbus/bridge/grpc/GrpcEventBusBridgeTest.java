package io.vertx.tests.eventbus.bridge.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.util.Durations;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.event.v1alpha.*;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class GrpcEventBusBridgeTest extends GrpcEventBusBridgeTestBase {

  private static final SocketAddress BRIDGE_ADDRESS = SocketAddress.inetSocketAddress(7000, "localhost");


  private GrpcClient client;
  private EventBusBridgeGrpcClient grpcClient;

  @Override
  public void before() {
    super.before();

    client = GrpcClient.client(vertx);

    grpcClient = EventBusBridgeGrpcClient.create(client, BRIDGE_ADDRESS);
  }

  @After
  public void after(TestContext context) {
    Async async = context.async();

    super.after(context);

    if (client != null) {
      client.close().onComplete(c -> vertx.close().onComplete(context.asyncAssertSuccess(h -> async.complete())));
    }
  }

  @Test
  public void testPing() {
    Empty resp = grpcClient.ping(Empty.getDefaultInstance()).await();
  }

  private <T> T testSendWithBody(T payload) throws Exception {
    return testSendWithBody(jsonToPayload(payload));
  }

  private <T> T testSendWithBody(JsonValue payload) throws Exception {
    return testSendWithBody("test", payload);
  }

  private <T> T testSendWithBody(String address, JsonValue payload) throws Exception {
    CompletableFuture<T> body = consumeEventBusMessage(address);

    SendOp request = SendOp.newBuilder()
      .setAddress(address)
      .setBody(payload)
      .build();

    grpcClient.send(request).await();

    return body.get(5, TimeUnit.SECONDS);
  }

  private <T> T testPublishWithBody(T payload) throws Exception {
    return testPublishWithBody(jsonToPayload(payload));
  }

  private <T> T testPublishWithBody(JsonValue payload) throws Exception {
    return testPublishWithBody("test", payload);
  }

  private <T> T testPublishWithBody(String address, JsonValue payload) throws Exception {
    CompletableFuture<T> body = consumeEventBusMessage(address);

    PublishOp request = PublishOp.newBuilder()
      .setAddress(address)
      .setBody(payload)
      .build();

    grpcClient.publish(request).await();

    return body.get(5, TimeUnit.SECONDS);
  }

  private <T> CompletableFuture<T> consumeEventBusMessage(String address) {
    CompletableFuture<T> body = new CompletableFuture<>();

    vertx.eventBus().consumer(address, (Message<T> msg) -> {
      body.complete(msg.body());
    });

    return body;
  }

  private <T> T testRequestWithBody(T body) {
    return testRequestWithBody(jsonToPayload(body));
  }

  private <T> T testRequestWithBody(JsonValue body) {
    return testRequestWithBody("hello", body);
  }

  private <T> T testRequestWithBody(String address, JsonValue body) {
    RequestOp request = RequestOp.newBuilder()
      .setAddress(address)
      .setBody(body)
      .setTimeout(Durations.fromMillis(5000))
      .build();
    EventBusMessage response = grpcClient.request(request).await();
    return valueToJson(response.getBody());
  }

  @Test
  public void testSendWithStringBody() throws Exception {
    JsonObject body = testSendWithBody(new JsonObject().put("message", "simple string message"));
    assertEquals("simple string message", body.getString("message"));
  }

  @Test
  public void testSendWithNumericBody() throws Exception {
    Double body = testSendWithBody(5.1D);
    assertEquals(5.1, body, 0.01D);
  }

  @Test
  public void testSendWithBooleanBody() throws Exception {
    Boolean body = testSendWithBody(true);
    assertTrue(body);
  }

  @Test
  public void testSendWithNullBody() throws Exception {
    Object body = testSendWithBody((Object)null);
    assertNull(body);
  }

  @Test
  public void testSendWithNestedObjectBody() throws Exception {
    JsonObject nestedBody = new JsonObject()
      .put("name", "Julien")
      .put("address", new JsonObject()
        .put("street", "5 Avenue Anatole France")
        .put("city", "Paris")
        .put("zipcode", 75007)
        .put("contact", new JsonObject()
          .put("email", "julien@vertx.io")
          .put("phone", "+99-9-99-99-99-99")));
    JsonObject body = testSendWithBody(nestedBody);
    assertEquals("Julien", body.getString("name"));
    JsonObject address = body.getJsonObject("address");
    assertNotNull(address);
    assertEquals("5 Avenue Anatole France", address.getString("street"));
    assertEquals("Paris", address.getString("city"));
    assertEquals(75007, (int)address.getInteger("zipcode"));
    JsonObject contact = address.getJsonObject("contact");
    assertNotNull(contact);
    assertEquals("julien@vertx.io", contact.getString("email"));
    assertEquals("+99-9-99-99-99-99", contact.getString("phone"));
  }

  @Test
  public void testSendWithEmptyJsonObjectBody() throws Exception {
    JsonObject body = testSendWithBody(new JsonObject());
    assertTrue(body.isEmpty());
  }

  @Test
  public void testSendWithMissingBody() throws Exception {
    try {
      testSendWithBody(JsonValue.newBuilder().build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testSendWithInvalidBody() throws Exception {
    try {
      testSendWithBody(JsonValue.newBuilder().setText("ABC").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testSendWithBinaryPayload(TestContext context) throws Exception {
    JsonObject binaryContent = new JsonObject()
      .put("type", "binary-data")
      .put("name", "Julien")
      .put("content", "vertx-binary-test");
    JsonValue binaryPayload = JsonValue.newBuilder()
      .setBinary(com.google.protobuf.ByteString.copyFromUtf8(binaryContent.encode()))
      .build();
    JsonObject body = testSendWithBody(binaryPayload);
    assertEquals("binary-data", body.getString("type"));
    assertEquals("Julien", body.getString("name"));
    assertEquals("vertx-binary-test", body.getString("content"));
  }

  @Test
  public void testSendWithTextPayload(TestContext context) throws Exception {
    JsonObject textContent = new JsonObject()
      .put("type", "text-message")
      .put("author", "Julien")
      .put("message", "vertx-text-payload");
    JsonValue textPayload = JsonValue.newBuilder()
      .setText(textContent.encode())
      .build();
    JsonObject body = testSendWithBody(textPayload);
    context.assertEquals("text-message", body.getString("type"));
    context.assertEquals("Julien", body.getString("author"));
    context.assertEquals("vertx-text-payload", body.getString("message"));
  }

  @Test
  public void testSendToUnPermittedAddress() throws Exception {
    try {
      testSendWithBody("not-declared", JsonValue.newBuilder().setText("{}").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.PERMISSION_DENIED, e.actualStatus());
    }
  }

  @Test
  public void testSendToUnauthorizedAddress() throws Exception {
    AtomicInteger checks = new AtomicInteger();
    eventHandler = event -> {
      if (event.type() == BridgeEventType.SEND && event.getRawMessage().getString("address").equals("test")) {
        checks.incrementAndGet();
        event.succeed(false);
      } else {
        event.succeed(true);
      }
    };
    try {
      testSendWithBody("test", JsonValue.newBuilder().setText("{}").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.PERMISSION_DENIED, e.actualStatus());
      assertEquals(1, checks.get());
    }
  }

  @Test
  public void testSendToUnregisteredAddress() {
    try {
      SendOp request = SendOp.newBuilder()
        .setAddress("test")
        .setBody(JsonValue.newBuilder().setText("{}").build())
        .build();
      grpcClient.send(request).await();
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.NOT_FOUND, e.actualStatus());
    }
  }

  @Test
  public void testSendWithMissingAddress() {
    try {
      SendOp request = SendOp.newBuilder()
        .setBody(JsonValue.newBuilder().setText("{}").build())
        .build();
      grpcClient.send(request).await();
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testRequest() {
    JsonObject requestBody = new JsonObject()
      .put("value", "vert.x")
      .put("timestamp", System.currentTimeMillis())
      .put("metadata", new JsonObject()
        .put("source", "grpc-test")
        .put("version", "1.0"));
    JsonObject responseBody = testRequestWithBody(requestBody);
    assertEquals("Hello vert.x", responseBody.getString("value"));
  }

  @Test
  public void testRequestWithComplexJsonObjectBody(TestContext context) {
    JsonObject complexBody = new JsonObject()
      .put("value", "getUserProfile")
      .put("userId", 12345)
      .put("includePermissions", true)
      .put("filters", new JsonObject()
        .put("activeOnly", true)
        .put("departments", new JsonObject()
          .put("include", "engineering")
          .put("exclude", "legacy")))
      .put("metadata", new JsonObject()
        .put("requestId", "req-001")
        .put("timestamp", System.currentTimeMillis())
        .putNull("correlationId"));
    JsonObject responseBody = testRequestWithBody(complexBody);
    context.assertEquals("Hello getUserProfile", responseBody.getString("value"));
  }

  @Test
  public void testRequestWithBinaryPayload(TestContext context) {
    JsonObject binaryRequestContent = new JsonObject()
      .put("value", "getBinaryProfile")
      .put("userId", 9999)
      .put("format", "binary");
    JsonValue binaryPayload = JsonValue.newBuilder()
      .setBinary(com.google.protobuf.ByteString.copyFromUtf8(binaryRequestContent.encode()))
      .build();
    JsonObject responseBody = testRequestWithBody(binaryPayload);
    context.assertEquals("Hello getBinaryProfile", responseBody.getString("value"));
  }

  @Test
  public void testRequestWithTextPayload(TestContext context) {
    JsonObject textRequestContent = new JsonObject()
      .put("value", "getTextProfile")
      .put("userId", 8888)
      .put("format", "text");
    JsonValue textPayload = JsonValue.newBuilder()
      .setText(textRequestContent.encode())
      .build();
    JsonObject responseBody = testRequestWithBody(textPayload);
    context.assertEquals("Hello getTextProfile", responseBody.getString("value"));
  }

  @Test
  public void testRequestWithMissingBody() throws Exception {
    try {
      testRequestWithBody(JsonValue.newBuilder().build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testRequestWithInvalidBody() throws Exception {
    try {
      testRequestWithBody(JsonValue.newBuilder().setText("ABC").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testRequestToUnPermittedAddress() throws Exception {
    try {
      testRequestWithBody("not-declared", JsonValue.newBuilder().setText("{}").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.PERMISSION_DENIED, e.actualStatus());
    }
  }

  @Test
  public void testRequestToUnauthorizedAddress() throws Exception {
    AtomicInteger checks = new AtomicInteger();
    eventHandler = event -> {
      if (event.type() == BridgeEventType.SEND && event.getRawMessage().getString("address").equals("hello")) {
        checks.incrementAndGet();
        event.succeed(false);
      } else {
        event.succeed(true);
      }
    };
    try {
      testRequestWithBody("hello", JsonValue.newBuilder().setText("{}").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.PERMISSION_DENIED, e.actualStatus());
      assertEquals(1, checks.get());
    }
  }

  @Test
  public void testRequestToUnregisteredAddress() {
    try {
      RequestOp request = RequestOp.newBuilder()
        .setAddress("test")
        .setBody(JsonValue.newBuilder().setText("{}").build())
        .build();
      grpcClient.request(request).await();
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.NOT_FOUND, e.actualStatus());
    }
  }

  @Test
  public void testRequestWithMissingAddress() {
    try {
      RequestOp request = RequestOp.newBuilder()
        .setBody(JsonValue.newBuilder().setText("{}").build())
        .build();
      grpcClient.request(request).await();
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testReplyToRequest(TestContext context) {

    Async async = context.async();

    vertx.eventBus().<Double>consumer("the-address", msg -> {
      msg.<Double>replyAndRequest(msg.body() + 1)
        .onComplete(context.asyncAssertSuccess(reply -> {
          context.assertEquals(2.0, reply.body());
          async.complete();
        }));
    });

    RequestOp request = RequestOp.newBuilder()
      .setAddress("the-address")
      .setBody(jsonToPayload(1))
      .build();

    EventBusMessage msg = grpcClient.request(request).await();
    assertFalse(msg.getReplyAddress().isEmpty());

    SendOp send = SendOp.newBuilder()
      .setAddress(msg.getReplyAddress())
      .setBody(jsonToPayload(2))
      .build();
    grpcClient.send(send).await();

    async.awaitSuccess(5_000);
  }

  @Test
  public void testReplyToSubscribe(TestContext context) {
    SubscribeOp sub = SubscribeOp.newBuilder()
      .setAddress("the-address")
      .build();

    ReadStream<EventBusMessage> messages = grpcClient.subscribe(sub).await();
    messages.handler(msg -> {
      assertFalse(msg.getReplyAddress().isEmpty());
      SendOp send = SendOp.newBuilder()
        .setAddress(msg.getReplyAddress())
        .setBody(jsonToPayload(2))
        .build();
      grpcClient.send(send);
    });

    Message<Object> reply = vertx.eventBus().request("the-address", "the-msg").await();
    assertEquals(2.0, reply.body());
  }

  @Test
  public void testReplyTimeout(TestContext context) {

    Async async = context.async();

    vertx.eventBus().<Double>consumer("the-address", msg -> {
      msg.<Double>replyAndRequest(msg.body() + 1)
        .onComplete(context.asyncAssertFailure(reply -> {
          async.complete();
        }));
    });

    RequestOp request = RequestOp.newBuilder()
      .setAddress("the-address")
      .setBody(jsonToPayload(1))
      .build();

    EventBusMessage msg = grpcClient.request(request).await();
    assertFalse(msg.getReplyAddress().isEmpty());


    async.awaitSuccess(5_000);
  }

  @Test
  public void testPublish(TestContext context) {
    Async async = context.async();
    AtomicBoolean received = new AtomicBoolean(false);

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      JsonObject body = msg.body();
      context.assertEquals("notification", body.getString("type"));
      context.assertEquals("System update available", body.getString("message"));
      context.assertEquals("high", body.getString("priority"));
      context.assertNotNull(body.getJsonObject("details"));
      context.assertEquals("v2.1.0", body.getJsonObject("details").getString("version"));
      if (received.compareAndSet(false, true)) {
        async.complete();
      }
    });

    JsonObject notificationBody = new JsonObject()
      .put("type", "notification")
      .put("message", "System update available")
      .put("priority", "high")
      .put("timestamp", System.currentTimeMillis())
      .put("details", new JsonObject()
        .put("version", "v2.1.0")
        .put("size", 1024));

    PublishOp request = PublishOp.newBuilder()
      .setAddress("test")
      .setBody(jsonToPayload(notificationBody))
      .build();

    grpcClient.publish(request).onComplete(context.asyncAssertSuccess(response -> {

    }));

    async.awaitSuccess(5000);
  }

  @Test
  public void testPublishWithBinaryPayload(TestContext context) {
    Async async = context.async();
    AtomicBoolean received = new AtomicBoolean(false);

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      JsonObject body = msg.body();
      context.assertEquals("binary-notification", body.getString("type"));
      context.assertEquals("Julien", body.getString("sender"));
      context.assertEquals("vertx-binary-publish", body.getString("content"));
      context.assertEquals(3, body.getInteger("priority"));

      if (received.compareAndSet(false, true)) {
        async.complete();
      }
    });

    JsonObject binaryNotificationContent = new JsonObject()
      .put("type", "binary-notification")
      .put("sender", "Julien")
      .put("content", "vertx-binary-publish")
      .put("priority", 3);
    JsonValue binaryPayload = JsonValue.newBuilder()
      .setBinary(com.google.protobuf.ByteString.copyFromUtf8(binaryNotificationContent.encode()))
      .build();

    PublishOp request = PublishOp.newBuilder()
      .setAddress("test")
      .setBody(binaryPayload)
      .build();

    grpcClient.publish(request).onComplete(context.asyncAssertSuccess(response -> {
    }));

    async.awaitSuccess(5000);
  }

  @Test
  public void testPublishWithTextPayload(TestContext context) {
    Async async = context.async();
    AtomicBoolean received = new AtomicBoolean(false);

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      JsonObject body = msg.body();
      context.assertEquals("text-notification", body.getString("type"));
      context.assertEquals("Julien", body.getString("author"));
      context.assertEquals("vertx-text-publish", body.getString("message"));
      context.assertEquals(1, body.getInteger("level"));

      if (received.compareAndSet(false, true)) {
        async.complete();
      }
    });

    JsonObject textNotificationContent = new JsonObject()
      .put("type", "text-notification")
      .put("author", "Julien")
      .put("message", "vertx-text-publish")
      .put("level", 1);
    JsonValue textPayload = JsonValue.newBuilder()
      .setText(textNotificationContent.encode())
      .build();

    PublishOp request = PublishOp.newBuilder()
      .setAddress("test")
      .setBody(textPayload)
      .build();

    grpcClient.publish(request).onComplete(context.asyncAssertSuccess(response -> {
    }));

    async.awaitSuccess(5000);
  }

  @Test
  public void testPublishWithMixedTypesJsonObjectBody(TestContext context) throws Exception {
    JsonObject mixedTypesBody = new JsonObject()
      .put("type", "vertx-event")
      .put("priority", 2.0)
      .put("urgent", true)
      .putNull("assignee")
      .put("tags", new JsonObject()
        .put("environment", "eventbus")
        .put("severity", 1));
    JsonObject body = testPublishWithBody(mixedTypesBody);
    context.assertEquals("vertx-event", body.getString("type"));
    context.assertEquals(2.0, body.getDouble("priority"));
    context.assertTrue(body.getBoolean("urgent"));
    context.assertNull(body.getValue("assignee"));
    JsonObject tags = body.getJsonObject("tags");
    context.assertNotNull(tags);
    context.assertEquals("eventbus", tags.getString("environment"));
    context.assertEquals(1, tags.getInteger("severity"));
  }

  @Test
  public void testPublishWithMissingBody() throws Exception {
    try {
      testPublishWithBody(JsonValue.newBuilder().build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testPublishWithInvalidBody() throws Exception {
    try {
      testPublishWithBody(JsonValue.newBuilder().setText("ABC").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testPublishToUnPermittedAddress() throws Exception {
    try {
      testPublishWithBody("not-declared", JsonValue.newBuilder().setText("{}").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.PERMISSION_DENIED, e.actualStatus());
    }
  }

  @Test
  public void testPublishToUnauthorizedAddress() throws Exception {
    AtomicInteger checks = new AtomicInteger();
    eventHandler = event -> {
      if (event.type() == BridgeEventType.PUBLISH && event.getRawMessage().getString("address").equals("hello")) {
        checks.incrementAndGet();
        event.succeed(false);
      } else {
        event.succeed(true);
      }
    };
    try {
      testPublishWithBody("hello", JsonValue.newBuilder().setText("{}").build());
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.PERMISSION_DENIED, e.actualStatus());
      assertEquals(1, checks.get());
    }
  }

  @Test
  public void testPublishToUnregisteredAddress() {
    try {
      PublishOp op = PublishOp.newBuilder()
        .setAddress("test")
        .setBody(JsonValue.newBuilder().setText("{}").build())
        .build();
      grpcClient.publish(op).await();
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.NOT_FOUND, e.actualStatus());
    }
  }

  @Test
  public void testPublishWithMissingAddress() {
    try {
      RequestOp request = RequestOp.newBuilder()
        .setBody(JsonValue.newBuilder().setText("{}").build())
        .build();
      grpcClient.request(request).await();
      fail();
    } catch (InvalidStatusException e) {
      assertEquals(GrpcStatus.INVALID_ARGUMENT, e.actualStatus());
    }
  }

  @Test
  public void testSubscribeWithProtoType(TestContext context) {
    testSubscribe(context, JsonValueFormat.proto);
  }

  @Test
  public void testSubscribeWithBinaryType(TestContext context) {
    testSubscribe(context, JsonValueFormat.binary);
  }

  private void testSubscribe(TestContext context, JsonValueFormat format) {
    Async async = context.async();
    SubscribeOp request = SubscribeOp.newBuilder().setAddress("ping").setMessageBodyFormat(format).build();

    grpcClient.subscribe(request).onComplete(context.asyncAssertSuccess(stream -> {

      MultiMap headers = ((GrpcClientResponse<?, ?>) stream).headers();
      String consumerId = headers.get("vertx-event-bus-consumer-id");

      stream.handler(response -> {

        context.assertEquals("ping", response.getAddress());
        context.assertNotNull(response.getBody());

        JsonValue body = response.getBody();
        JsonObject jsonBody = valueToJson(body, format);
        context.assertEquals("hi", jsonBody.getString("value"));

        UnsubscribeOp unsubRequest = UnsubscribeOp.newBuilder()
          .setConsumerId(consumerId)
          .build();

        grpcClient.unsubscribe(unsubRequest).onComplete(context.asyncAssertSuccess(unsubResponse -> async.complete()));
      });
    }));

    async.awaitSuccess(5000);
  }

  private void testSubscribe(TestContext context, String address, Supplier<Object> bodySupplier, Consumer<JsonValue> checker) {
    Async async = context.async();

    // Update the ping consumer to send a complex body
    long timerID = vertx.setPeriodic(10, id -> {
      vertx.eventBus().send(address, bodySupplier.get());
    });

    SubscribeOp request = SubscribeOp.newBuilder().setAddress(address).build();

    ReadStream<EventBusMessage> stream = grpcClient.subscribe(request).await();
    AtomicInteger amount = new AtomicInteger(10);
    stream.handler(response -> {

      context.assertEquals(address, response.getAddress());
      context.assertNotNull(response.getBody());

      JsonValue body = response.getBody();

      try {
        checker.accept(body);
      } catch (Exception e) {
        vertx.cancelTimer(timerID);
        context.fail(e);
      }

      if (amount.decrementAndGet() == 0) {
        UnsubscribeOp unsubRequest = UnsubscribeOp.newBuilder()
          .setConsumerId(response.getConsumerId())
          .build();
        grpcClient
          .unsubscribe(unsubRequest)
          .onComplete(context.asyncAssertSuccess(unsubResponse -> async.complete()));
      }
    });

    async.awaitSuccess(5000);
  }

  @Test
  public void testSubscribeWithComplexBody(TestContext context) {
    testSubscribe(context, "complex-ping", () -> new JsonObject()
      .put("messageId", "vertx-msg-001")
      .put("timestamp", System.currentTimeMillis())
      .put("sender", new JsonObject()
        .put("service", "vertx-eventbus-bridge")
        .put("version", "4.0.0"))
      .put("payload", new JsonObject()
        .put("status", "active")
        .put("metrics", new JsonObject()
          .put("cpu", 50.0)
          .put("memory", 80.0)
          .put("uptime", 7200))), value -> {
      JsonObject jsonBody = valueToJson(value);

      assertEquals("vertx-msg-001", jsonBody.getString("messageId"));
      assertNotNull(jsonBody.getLong("timestamp"));

      JsonObject sender = jsonBody.getJsonObject("sender");
      assertNotNull(sender);
      assertEquals("vertx-eventbus-bridge", sender.getString("service"));
      assertEquals("4.0.0", sender.getString("version"));

      JsonObject payload = jsonBody.getJsonObject("payload");
      assertNotNull(payload);
      assertEquals("active", payload.getString("status"));

      JsonObject metrics = payload.getJsonObject("metrics");
      assertNotNull(metrics);
      assertEquals(50.0, metrics.getDouble("cpu"), 0.0001);
      assertEquals(80.0, metrics.getDouble("memory"), 0.0001);
      assertEquals(7200, metrics.getInteger("uptime"), 0.0001);
    });
  }

  @Test
  public void testSubscribeWithNumber(TestContext context) {
    testSubscribe(context, "complex-ping", () -> 4.1D, value -> {
      Double v = valueToJson(value);
      assertEquals(4.1D, v, 0.0001D);
    });
  }

  @Test
  public void testSubscribeWithString(TestContext context) {
    testSubscribe(context, "complex-ping", () -> "the-string", value -> {
      String v = valueToJson(value);
      assertEquals("the-string", v);
    });
  }

  @Test
  public void testUnsubscribeWithoutReceivingMessage(TestContext context) {
    Async async = context.async();
    AtomicReference<String> consumerId = new AtomicReference<>();
    SubscribeOp request = SubscribeOp.newBuilder().setAddress("ping").build();

    grpcClient.subscribe(request).onComplete(context.asyncAssertSuccess(stream -> stream.handler(response -> {
      consumerId.set(response.getConsumerId());
      UnsubscribeOp unsubRequest = UnsubscribeOp.newBuilder()
        .setConsumerId(consumerId.get())
        .build();

      grpcClient.unsubscribe(unsubRequest).onComplete(context.asyncAssertSuccess(unsubResponse -> {
        vertx.setTimer(1000, id -> async.complete());
        stream.handler(msg -> context.fail("Received message after unsubscribe"));
      }));
    })));

    async.awaitSuccess(5000);
  }

  @Test
  public void testUnsubscribeInvalidConsumerId(TestContext context) {
    UnsubscribeOp unsubRequest = UnsubscribeOp.newBuilder()
      .setConsumerId("invalid-consumer-id")
      .build();

    try {
      grpcClient.unsubscribe(unsubRequest).await();
      fail();
    } catch (Exception expected) {
    }
  }

  @Test
  public void testMultipleSubscribeAndUnsubscribe(TestContext context) {
    Async async = context.async(2);
    AtomicReference<String> consumerId1 = new AtomicReference<>();
    AtomicReference<String> consumerId2 = new AtomicReference<>();
    SubscribeOp request = SubscribeOp.newBuilder().setAddress("ping").build();

    // First subscription
    grpcClient.subscribe(request).onComplete(context.asyncAssertSuccess(stream1 -> stream1.handler(response -> {
      if (consumerId1.get() != null) {
        return;
      }

      consumerId1.set(response.getConsumerId());

      // Second subscription
      grpcClient.subscribe(request).onComplete(context.asyncAssertSuccess(stream2 -> stream2.handler(response2 -> {
        if (consumerId2.get() != null) {
          return;
        }

        consumerId2.set(response2.getConsumerId());
        context.assertNotEquals(consumerId1.get(), consumerId2.get());

        UnsubscribeOp unsubRequest1 = UnsubscribeOp.newBuilder()
          .setConsumerId(consumerId1.get())
          .build();

        grpcClient.unsubscribe(unsubRequest1).onComplete(context.asyncAssertSuccess(unsubResponse1 -> {
          async.countDown();
          UnsubscribeOp unsubRequest2 = UnsubscribeOp.newBuilder()
            .setConsumerId(consumerId2.get())
            .build();

          grpcClient.unsubscribe(unsubRequest2).onComplete(context.asyncAssertSuccess(unsubResponse2 -> async.countDown()));
        }));
      })));
    })));

    async.awaitSuccess(5000);
  }

  @Test
  public void testMultipleMessagesInStream(TestContext context) {
    Async async = context.async();
    AtomicInteger messageCount = new AtomicInteger(0);
    AtomicReference<String> consumerId = new AtomicReference<>();
    AtomicReference<Long> firstMessageTime = new AtomicReference<>();
    AtomicReference<Long> lastMessageTime = new AtomicReference<>();
    SubscribeOp request = SubscribeOp.newBuilder().setAddress("ping").build();

    int expectedMessages = 3;

    grpcClient.subscribe(request).onComplete(context.asyncAssertSuccess(stream -> stream.handler(response -> {
      if (consumerId.get() == null) {
        consumerId.set(response.getConsumerId());
      }

      context.assertEquals("ping", response.getAddress());
      context.assertNotNull(response.getBody());

      JsonValue body = response.getBody();
      JsonObject jsonBody = valueToJson(body);
      context.assertEquals("hi", jsonBody.getString("value"));

      long currentTime = System.currentTimeMillis();
      if (firstMessageTime.get() == null) {
        firstMessageTime.set(currentTime);
      }

      lastMessageTime.set(currentTime);

      int count = messageCount.incrementAndGet();

      if (count >= expectedMessages) {
        long timeDifference = lastMessageTime.get() - firstMessageTime.get();
        context.assertTrue(timeDifference >= 1000, "Expected delay between messages, but got: " + timeDifference + "ms");
        System.out.println("[DEBUG] Time difference between first and last message: " + timeDifference + "ms");

        UnsubscribeOp unsubRequest = UnsubscribeOp.newBuilder()
          .setConsumerId(consumerId.get())
          .build();

        grpcClient.unsubscribe(unsubRequest).onComplete(context.asyncAssertSuccess(unsubResponse -> async.complete()));
      }
    })));

    async.awaitSuccess(5000);
  }

  @Test
  public void testCancelSubscriptionStream(TestContext context) {
    testSubscriptionStreamError(context, stream -> stream.request().cancel());
  }

  @Test
  public void testCloseSubscriptionStream(TestContext context) {
    testSubscriptionStreamError(context, stream -> stream.request().connection().close());
  }

  private void testSubscriptionStreamError(TestContext context, Consumer<GrpcClientResponse<?, ?>> failureHandler) {
    Async async = context.async();
    AtomicInteger messageCount = new AtomicInteger(0);

    eventHandler = event -> {
      switch (event.type()) {
        case SOCKET_CLOSED:
        case UNREGISTER:
          async.complete();
          break;
      }
      event.succeed(true);
    };

    SubscribeOp request = SubscribeOp.newBuilder().setAddress("ping").build();

    grpcClient.subscribe(request).onComplete(context.asyncAssertSuccess(stream -> {
      stream.handler(response -> {
        if (messageCount.getAndIncrement() == 1) {
          failureHandler.accept((GrpcClientResponse<?, ?>) stream);
        }
      });
    }));

    async.awaitSuccess(5000);
  }
}
