package examples.grpc;

import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.Value;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.eventbus.bridge.grpc.GrpcBridgeOptions;
import io.vertx.eventbus.bridge.grpc.GrpcEventBusBridge;
import io.vertx.eventbus.bridge.grpc.GrpcEventBusBridgeService;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.grpc.event.v1alpha.*;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.client.GrpcClient;

public class GrpcBridgeExamples {

  public void createServer(Vertx vertx) {
    // Configure bridge options
    GrpcBridgeOptions options = new GrpcBridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress("hello"))
      .addInboundPermitted(new PermittedOptions().setAddress("echo"))
      .addOutboundPermitted(new PermittedOptions().setAddress("news"));

    // Create the bridge
    GrpcEventBusBridge bridge = GrpcEventBusBridge.create(
      vertx,
      options,
      7000,  // Port
      event -> {
        // Optional event handler for bridge events
        System.out.println("Bridge event: " + event.type());
        event.complete(true);
      }
    );

    // Start the bridge
    bridge.listen().onComplete(ar -> {
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
    EventBusBridgeGrpcClient grpcClient = EventBusBridgeGrpcClient.create(client, socketAddress);
  }

  public void sendMessage(EventBusBridgeGrpcClient grpcClient) {
    // Create a message
    JsonObject message = new JsonObject().put("value", "Hello from gRPC client");

    // Convert to Protobuf Struct
    JsonValue messageBody = toJsonValue(message);

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

    // Convert to Protobuf Struct
    JsonValue messageBody = toJsonValue(message);

    // Create the request with timeout
    RequestOp request = RequestOp.newBuilder()
      .setAddress("hello")
      .setBody(messageBody)
      .setTimeout(Duration.newBuilder().setSeconds(5).build())  // 5 seconds timeout
      .build();

    // Send the request
    grpcClient.request(request).onComplete(ar -> {
      if (ar.succeeded()) {
        EventBusMessage response = ar.result();
        // Convert Protobuf Struct to JsonObject
        JsonObject responseBody = structToJson(response.getBody());
        System.out.println("Received response: " + responseBody);
      } else {
        System.err.println("Request failed: " + ar.cause());
      }
    });
  }

  public void publishMessage(EventBusBridgeGrpcClient grpcClient) {
    // Create a message
    JsonObject message = new JsonObject().put("value", "Broadcast message");

    // Convert to Protobuf Struct
    JsonValue messageBody = toJsonValue(message);

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
          JsonObject messageBody = structToJson(message.getBody());
          System.out.println("Received message: " + messageBody);
        });

        // Handle end of stream
        stream.endHandler(v -> {
          System.out.println("Stream ended");
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

  private JsonObject structToJson(JsonValue value) {
    // This is a placeholder for the actual conversion method
    // In a real implementation, you would convert Protobuf Struct to JsonObject
    return new JsonObject();
  }

  public void createCustomServerWithBridgeService(Vertx vertx) {
    // Configure bridge options
    GrpcBridgeOptions options = new GrpcBridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress("hello"))
      .addInboundPermitted(new PermittedOptions().setAddress("echo"))
      .addOutboundPermitted(new PermittedOptions().setAddress("news"));

    // Create the EventBus bridge service
    GrpcEventBusBridgeService bridgeService = GrpcEventBusBridgeService.create(
      vertx.eventBus(),
      options,
      event -> {
        // Optional event handler for bridge events
        System.out.println("Bridge event: " + event.type());
        event.complete(true);
      }
    );

    // Create a custom gRPC server
    GrpcServer grpcServer = GrpcServer.server(vertx);

    // Bind the bridge service to the gRPC server
    bridgeService.bind(grpcServer);

    // Create an HTTP server and use the gRPC server as request handler
    vertx.createHttpServer()
      .requestHandler(grpcServer)
      .listen(7000)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.println("Custom gRPC server with EventBus bridge started on port 7000");
        } else {
          System.err.println("Failed to start custom gRPC server: " + ar.cause());
        }
      });
  }

  public void createServerWithMultipleServices(Vertx vertx) {
    // Create a custom gRPC server
    GrpcServer grpcServer = GrpcServer.server(vertx);

    // Add your custom services first
    // MyCustomService customService = new MyCustomService();
    // customService.bind(grpcServer);
    // AnotherCustomService anotherService = new AnotherCustomService();
    // anotherService.bind(grpcServer);

    // Configure and add the EventBus bridge service
    GrpcBridgeOptions options = new GrpcBridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress("hello"))
      .addOutboundPermitted(new PermittedOptions().setAddress("notifications"));

    GrpcEventBusBridgeService bridgeService = GrpcEventBusBridgeService.create(
      vertx.eventBus(),
      options
    );

    // Bind the bridge service alongside your other services
    bridgeService.bind(grpcServer);

    // Create an HTTP server and use the gRPC server as request handler
    vertx.createHttpServer()
      .requestHandler(grpcServer)
      .listen(8080)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.println("gRPC server with multiple services started on port 8080");
        } else {
          System.err.println("Failed to start gRPC server: " + ar.cause());
        }
      });
  }

  public void createBridgeServiceWithAdvancedConfig(Vertx vertx) {
    // Advanced bridge configuration
    GrpcBridgeOptions options = new GrpcBridgeOptions()
      // Inbound permissions (client -> EventBus)
      .addInboundPermitted(new PermittedOptions().setAddress("api.users"))
      .addInboundPermitted(new PermittedOptions().setAddress("api.orders"))
      .addInboundPermitted(new PermittedOptions().setAddressRegex("api\\.notifications\\..*"))

      // Outbound permissions (EventBus -> client)
      .addOutboundPermitted(new PermittedOptions().setAddress("events.user.created"))
      .addOutboundPermitted(new PermittedOptions().setAddress("events.order.updated"))
      .addOutboundPermitted(new PermittedOptions().setAddressRegex("events\\.system\\..*"));

    // Create the bridge service with advanced event handling
    GrpcEventBusBridgeService bridgeService = GrpcEventBusBridgeService.create(
      vertx.eventBus(),
      options,
      event -> {
        // Advanced bridge event handling
        switch (event.type()) {
          case SOCKET_CREATED:
            System.out.println("New gRPC client connected");
            break;
          case SOCKET_CLOSED:
            System.out.println("gRPC client disconnected");
            break;
          case SEND:
            System.out.println("Message sent to: " + event.getRawMessage().getString("address"));
            break;
          case PUBLISH:
            System.out.println("Message published to: " + event.getRawMessage().getString("address"));
            break;
          case RECEIVE:
            System.out.println("Message received from: " + event.getRawMessage().getString("address"));
            break;
          case REGISTER:
            System.out.println("Client registered for: " + event.getRawMessage().getString("address"));
            break;
          case UNREGISTER:
            System.out.println("Client unregistered from: " + event.getRawMessage().getString("address"));
            break;
        }

        // Always complete the event to allow it to proceed
        event.complete(true);
      }
    );

    // Create and configure the server
    GrpcServer grpcServer = GrpcServer.server(vertx);
    bridgeService.bind(grpcServer);

    // Create an HTTP server and use the gRPC server as request handler
    vertx.createHttpServer()
      .requestHandler(grpcServer)
      .listen(9000)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.println("Advanced gRPC EventBus bridge started on port 9000");
        } else {
          System.err.println("Failed to start advanced bridge: " + ar.cause());
        }
      });
  }
}
