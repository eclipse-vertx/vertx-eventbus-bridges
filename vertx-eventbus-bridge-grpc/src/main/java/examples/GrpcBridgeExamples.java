package examples;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.docgen.Source;
import io.vertx.eventbus.bridge.grpc.GrpcBridgeOptions;
import io.vertx.eventbus.bridge.grpc.GrpcEventBusBridge;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.grpc.event.v1alpha.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.client.GrpcClient;

@Source
public class GrpcBridgeExamples {

  public void createServer(Vertx vertx) {
    // Configure bridge options
    GrpcBridgeOptions options = new GrpcBridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress("hello"))
      .addInboundPermitted(new PermittedOptions().setAddress("echo"))
      .addOutboundPermitted(new PermittedOptions().setAddress("news"));

    // Create the bridge
    GrpcEventBusBridge bridge = GrpcEventBusBridge.create(vertx, options);

    // Create the gRPC server
    GrpcServer grpcServer = GrpcServer.server(vertx).addService(bridge);

    // Add the server
    grpcServer.addService(bridge);

    HttpServer server = vertx
      .createHttpServer(new HttpServerOptions()
        .setSsl(true)
        .setKeyCertOptions(new JksOptions()
          .setPath("/path/to/keycert.jks")
          .setPassword("the-password"))
      )
      .requestHandler(grpcServer);

    // Start the bridge
    server.listen(443).onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("gRPC EventBus Bridge started");
      } else {
        System.err.println("Failed to start gRPC EventBus Bridge: " + ar.cause());
      }
    });
  }

  public void createClient(Vertx vertx) {
    // Create the gRPC client
    GrpcClient client = GrpcClient.client(vertx);
    SocketAddress socketAddress = SocketAddress.inetSocketAddress(7000, "localhost");
    EventBusBridgeGrpcClient bridgeClient = EventBusBridgeGrpcClient.create(client, socketAddress);
  }

  public void createJsonValueFromText() {
    JsonValue jsonValue = JsonValue.newBuilder().setText("4").build();

    JsonObject json = new JsonObject().put("name", "Julien");
    jsonValue = JsonValue.newBuilder().setText(json.encode()).build();
  }

  public void createJsonValueFromBinary() {
    JsonValue jsonValue = JsonValue.newBuilder().setBinary(ByteString.copyFromUtf8("4")).build();

    JsonObject json = new JsonObject().put("name", "Julien");
    jsonValue = JsonValue.newBuilder().setBinary(ByteString.copyFromUtf8(json.encode())).build();
  }

  public void createJsonValueFromStruct() {
    JsonValue jsonValue = JsonValue.newBuilder().setProto(
      Value.newBuilder().setNumberValue(4)).build();

    jsonValue = JsonValue.newBuilder().setProto(
      Value.newBuilder().setStructValue(Struct
        .newBuilder()
        .putFields("name", Value.newBuilder().setStringValue("Julien").build()))
    ).build();
  }

  public void sendMessage(EventBusBridgeGrpcClient grpcClient) {
    // Create a message
    JsonObject message = new JsonObject().put("value", "Hello from gRPC client");

    // Convert to Protobuf Struct
    JsonValue messageBody = JsonValue.newBuilder().setText(message.encode()).build();

    // Create the request
    SendOp request = SendOp.newBuilder()
      .setAddress("hello")
      .setBody(messageBody)
      .build();

    // Send the message
    grpcClient.send(request).onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("Message sent successfully");
      } else {
        System.err.println("Failed to send message: " + ar.cause());
      }
    });
  }

  public void requestResponse(EventBusBridgeGrpcClient grpcClient) {
    // Create a message
    JsonObject message = new JsonObject().put("value", "Hello from gRPC client");

    // Convert to JsonValue
    JsonValue messageBody = JsonValue.newBuilder().setText(message.encode()).build();

    // Create the request with timeout
    RequestOp request = RequestOp.newBuilder()
      .setAddress("hello")
      .setBody(messageBody)
      .setReplyBodyFormatValue(JsonValueFormat.text_VALUE) // Ask for encoded strings
      .setTimeout(Duration.newBuilder().setSeconds(10).build())  // 10 seconds timeout
      .build();

    // Send the request
    grpcClient.request(request).onComplete(ar -> {
      if (ar.succeeded()) {
        EventBusMessage response = ar.result();
        // Convert Protobuf Struct to JsonObject
        Object responseBody = Json.decodeValue(response.getBody().getText());
        System.out.println("Received response: " + responseBody);
      } else {
        System.err.println("Request failed: " + ar.cause());
      }
    });
  }

  public void publishMessage(EventBusBridgeGrpcClient grpcClient) {
    // Create a message
    JsonObject message = new JsonObject().put("value", "Broadcast message");

    // Convert to JsonValue
    JsonValue messageBody = JsonValue.newBuilder().setText(message.encode()).build();

    // Create the request
    PublishOp request = PublishOp.newBuilder()
      .setAddress("news")
      .setBody(messageBody)
      .build();

    // Publish the message
    grpcClient.publish(request).onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("Message published successfully");
      } else {
        System.err.println("Failed to publish message: " + ar.cause());
      }
    });
  }

  public void subscribeToMessages(EventBusBridgeGrpcClient grpcClient) {
    // Create the subscription request
    SubscribeOp request = SubscribeOp.newBuilder()
      .setAddress("news")
      .setMessageBodyFormatValue(JsonValueFormat.text_VALUE) // Ask for encoded strings
      .build();

    // Subscribe to the address
    grpcClient.subscribe(request).onComplete(ar -> {
      if (ar.succeeded()) {
        // Get the stream
        ReadStream<EventBusMessage> stream = ar.result();

        // Set a handler for incoming messages
        stream.handler(message -> {
          // Store the consumer ID for later unsubscribing
          String consumerId = message.getConsumerId();

          // Convert Protobuf Struct to JsonObject
          Object messageBody = Json.decodeValue(message.getBody().getText());
          System.out.println("Received message: " + messageBody);
        });

        // Handle errors
        stream.exceptionHandler(err -> {
          System.err.println("Stream error: " + err.getMessage());
        });
      } else {
        System.err.println("Failed to subscribe: " + ar.cause());
      }
    });
  }

  public void unsubscribeFromMessages(EventBusBridgeGrpcClient grpcClient, String consumerId) {
    // Create the unsubscribe request with the consumer ID received during subscription
    UnsubscribeOp request = UnsubscribeOp.newBuilder()
      .setConsumerId(consumerId)  // The consumer ID received in the subscription
      .build();

    // Unsubscribe
    grpcClient.unsubscribe(request).onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("Unsubscribed successfully");
      } else {
        System.err.println("Failed to unsubscribe: " + ar.cause());
      }
    });
  }

  public void replyingToAnEventBusMessage(EventBusBridgeGrpcClient grpcClient, ReadStream<EventBusMessage> streamOfMessages) {

    // Set a handler for incoming messages
    streamOfMessages.handler(message -> {

      String replyAddress = message.getReplyAddress();

      // Reply to the sender
      SendOp reply = SendOp.newBuilder()
        .setAddress(replyAddress)
        .setBody(message.getBody())
        .build();

      // Echo the message
      grpcClient.send(reply);
    });

  }

  public void healthCheck(EventBusBridgeGrpcClient grpcClient) {
    // Send a ping request
    grpcClient.ping(Empty.getDefaultInstance()).onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("Bridge is healthy");
      } else {
        System.err.println("Bridge health check failed: " + ar.cause());
      }
    });
  }

  // Helper methods for JSON <-> Struct conversion
  private JsonValue toJsonValue(JsonObject json) {
    // This is a placeholder for the actual conversion method
    // In a real implementation, you would convert JsonObject to Protobuf Struct
    return JsonValue.getDefaultInstance();
  }

  public void createBridgeServiceWithCustomEventHandler(Vertx vertx, HttpServerOptions serverOptions, GrpcBridgeOptions bridgeOptions) {
    // Create the bridge service with advanced event handling
    GrpcEventBusBridge bridgeService = GrpcEventBusBridge.builder(vertx)
      .with(bridgeOptions)
      .withEventHandler(bridgeEvent -> {

        // Advanced bridge event handling
        switch (bridgeEvent.type()) {
          case SOCKET_CREATED:
            System.out.println("New gRPC client connected");
            break;
          case SOCKET_CLOSED:
            System.out.println("gRPC client disconnected");
            break;
          case SEND:
            System.out.println("Message sent to: " + bridgeEvent.getRawMessage().getString("address"));
            break;
          case PUBLISH:
            System.out.println("Message published to: " + bridgeEvent.getRawMessage().getString("address"));
            break;
          case RECEIVE:
            System.out.println("Message received from: " + bridgeEvent.getRawMessage().getString("address"));
            break;
          case REGISTER:
            System.out.println("Client registered for: " + bridgeEvent.getRawMessage().getString("address"));
            break;
          case UNREGISTER:
            System.out.println("Client unregistered from: " + bridgeEvent.getRawMessage().getString("address"));
            break;
        }

        // Always complete the event to allow it to proceed
        bridgeEvent.complete(true);
      })
      .build();
  }
}
