package io.vertx.tests.eventbus.bridge.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.util.Durations;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.event.v1alpha.EventBusMessage;
import io.vertx.grpc.event.v1alpha.PublishOp;
import io.vertx.grpc.event.v1alpha.RequestOp;
import io.vertx.grpc.event.v1alpha.SubscribeOp;
import org.junit.Test;

import java.util.Base64;

import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.grpc.common.GrpcMediaType.GRPC_WEB_TEXT;
import static org.junit.Assert.assertEquals;

public class GrpcEventBusWebTest extends GrpcEventBusBridgeTestBase {

  private static final Base64.Encoder ENCODER = Base64.getEncoder();
  private static final Base64.Decoder DECODER = Base64.getDecoder();

  private static final int PREFIX_SIZE = 5;

  private static final String GRPC_STATUS = "grpc-status";
  private static final String STATUS_OK = GRPC_STATUS + ":" + 0 + "\r\n";

  private static final CharSequence USER_AGENT = HttpHeaders.createOptimized("X-User-Agent");
  private static final CharSequence GRPC_WEB_JAVASCRIPT_0_1 = HttpHeaders.createOptimized("grpc-web-javascript/0.1");
  private static final CharSequence GRPC_WEB = HttpHeaders.createOptimized("X-Grpc-Web");
  private static final CharSequence TRUE = HttpHeaders.createOptimized("1");

  private HttpClient client;

  public static BufferInternal encode(GrpcMessage message, boolean trailer) {
    boolean compressed = !message.encoding().equals("identity");
    return encode(message.payload(), compressed, trailer);
  }

  public static BufferInternal encode(Buffer payload, boolean compressed, boolean trailer) {
    int len = payload.length();
    BufferInternal encoded = BufferInternal.buffer(5 + len);
    encoded.appendByte((byte) ((trailer ? 0x80 : 0x00) | (compressed ? 0x01 : 0x00)));
    encoded.appendInt(len);
    encoded.appendBuffer(payload);
    return encoded;
  }

  public void before() {
    super.before();

    // Create HTTP client with appropriate options for gRPC-Web
    HttpClientOptions options = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(7000);
    this.client = vertx.createHttpClient(options);

    ping().await();
  }

  @Override
  public void after(TestContext context) {
    super.after(context);

    Async async = context.async();

    if (client != null) {
      client.close().onComplete(v -> vertx.close().onComplete(context.asyncAssertSuccess(h -> async.complete())));
    }
  }

  @Test
  public void testRequestResponse(TestContext context) {
    Async async = context.async();

    // Send a request to the "hello" address
    JsonObject request = new JsonObject().put("value", "Vert.x");

    request("hello", request, 5000).onSuccess(response -> {
      // Verify the response
      context.assertTrue(response.containsKey("value"));
      context.assertEquals("Hello Vert.x", response.getString("value"));

      async.complete();
    }).onFailure(err -> context.fail("Request failed: " + err.getMessage()));

    async.awaitSuccess(5000);
  }

  @Test
  public void testEcho(TestContext context) {
    Async async = context.async();

    // Create a complex JSON object
    JsonObject request = new JsonObject()
      .put("string", "value")
      .put("number", 123)
      .put("boolean", true)
      .put("nested", new JsonObject().put("key", "value"));

    request("echo", request, 5000).onSuccess(response -> {
      context.assertEquals(request.getString("string"), response.getString("string"));
      context.assertEquals(request.getInteger("number"), response.getInteger("number"));
      context.assertEquals(request.getBoolean("boolean"), response.getBoolean("boolean"));
      context.assertEquals(request.getJsonObject("nested").getString("key"), response.getJsonObject("nested").getString("key"));

      async.complete();
    }).onFailure(err -> context.fail("Echo request failed: " + err.getMessage()));

    async.awaitSuccess(5000);
  }

  @Test
  public void testPublishToRestrictedAddress() {
    JsonObject message = new JsonObject().put("key", "value");
    publish("restricted-address", message).await();
  }

  @Test
  public void testSubscribeToAddress(TestContext context) {
    Async async = context.async();

    String address = "test-address";

    subscribe(address).onSuccess(buffer -> {
      // Simulate sending a message to the subscribed address
      vertx.eventBus().send(address, new JsonObject().put("key", "value"));

      // Wait for the message to be received
      vertx.setTimer(1000, id -> async.complete());
    }).onFailure(err -> context.fail("Subscription failed: " + err.getMessage()));

    async.awaitSuccess(5000);
  }

  protected MultiMap requestHeaders() {
    return HttpHeaders.headers()
      .add(ACCEPT, GRPC_WEB_TEXT)
      .add(CONTENT_TYPE, GRPC_WEB_TEXT)
      .add(USER_AGENT, GRPC_WEB_JAVASCRIPT_0_1)
      .add(GRPC_WEB, TRUE);
  }

  private Buffer encode(GrpcMessage message) {
    return encode(message, false);
  }

  private Buffer encode(Message message) {
    Buffer buffer = encode(GrpcMessage.message("identity", Buffer.buffer(message.toByteArray())));
    // The whole message must be encoded at once when sending
    return Buffer.buffer(ENCODER.encode(buffer.getBytes()));
  }

  private Buffer decodeBody(Buffer buffer) {
    // The server sends base64 encoded chunks of arbitrary size
    // All we know is that a 4-bytes block is always a valid base64 payload
    assertEquals(0, buffer.length() % 4);
    Buffer res = Buffer.buffer();
    for (int i = 0; i < buffer.length(); i += 4) {
      byte[] block = buffer.getBytes(i, i + 4);
      res.appendBytes(DECODER.decode(block));
    }
    return res;
  }

  public Future<JsonObject> request(String address, JsonObject message, long timeout) {
    Promise<JsonObject> promise = Promise.promise();

    RequestOp request = RequestOp.newBuilder()
      .setAddress(address)
      .setBody(GrpcEventBusBridgeTestBase.jsonToPayload(message))
      .setTimeout(Durations.fromMillis(timeout))
      .build();

    client.request(HttpMethod.POST, "/vertx.event.v1alpha.EventBusBridge/Request")
      .compose(httpRequest -> {
        requestHeaders().forEach(httpRequest::putHeader);

        return httpRequest.send(encode(request)).onSuccess(response -> {
            if (response.statusCode() == 200) {
              response.body().onSuccess(buffer -> {
                try {
                  Buffer body = decodeBody(buffer);
                  int pos = 0;

                  Buffer prefix = body.getBuffer(pos, PREFIX_SIZE);
                  assertEquals(0x00, prefix.getUnsignedByte(0)); // Uncompressed message
                  int len = prefix.getInt(1);
                  pos += PREFIX_SIZE;

                  EventBusMessage eventMessage = EventBusMessage.parseFrom(body.getBuffer(pos, pos + len).getBytes());
                  pos += len;

                  Buffer trailer = body.getBuffer(pos, body.length());
                  assertEquals(0x80, trailer.getUnsignedByte(0)); // Uncompressed trailer
                  len = trailer.getInt(1);
                  assertEquals(STATUS_OK, trailer.getBuffer(PREFIX_SIZE, PREFIX_SIZE + len).toString());

                  promise.complete(GrpcEventBusBridgeTestBase.valueToJson(eventMessage.getBody()));
                } catch (Exception e) {
                  promise.fail("Failed to parse response: " + e.getMessage());
                }
              });
            } else {
              String error = "HTTP error: " + response.statusCode() + " " + response.statusMessage();
              promise.fail(error);
            }
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<Void> publish(String address, JsonObject message) {
    PublishOp request = PublishOp.newBuilder()
      .setAddress(address)
      .setBody(GrpcEventBusBridgeTestBase.jsonToPayload(message))
      .build();

    return client.request(HttpMethod.POST, "/vertx.event.v1alpha.EventBusBridge/Publish").compose(httpRequest -> {
        requestHeaders().forEach(httpRequest::putHeader);

        return httpRequest.send(encode(request))
          .expecting(HttpResponseExpectation.SC_OK)
          .expecting(resp -> resp.headers().contains(GRPC_STATUS))
          .compose(HttpClientResponse::end);
      });
  }

  public Future<Buffer> subscribe(String address) {
    Promise<Buffer> promise = Promise.promise();

    client.request(HttpMethod.POST, "/vertx.event.v1alpha.EventBusBridge/Subscribe").compose(httpRequest -> {
        requestHeaders().forEach(httpRequest::putHeader);

        SubscribeOp request = SubscribeOp.newBuilder()
          .setAddress(address)
          .build();

        return httpRequest.send(encode(request)).compose(response -> response.body().map(response))
          .onSuccess(response -> {
            if (response.statusCode() == 200) {
              response.body().onSuccess(buffer -> {
                promise.complete(decodeBody(buffer));
              });
            } else {
              String error = "HTTP error: " + response.statusCode() + " " + response.statusMessage();
              promise.fail(error);
            }
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<Void> ping() {
    Promise<Void> promise = Promise.promise();

    client.request(HttpMethod.POST, "/vertx.event.v1alpha.EventBusBridge/Ping").compose(httpRequest -> {
        requestHeaders().forEach(httpRequest::putHeader);
        return httpRequest.send(encode(Empty.getDefaultInstance()))
          .onSuccess(response -> {
            if (response.statusCode() == 200) {
              promise.complete();
            } else {
              String error = "HTTP error: " + response.statusCode() + " " + response.statusMessage();
              promise.fail(error);
            }
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);

    return promise.future();
  }
}
