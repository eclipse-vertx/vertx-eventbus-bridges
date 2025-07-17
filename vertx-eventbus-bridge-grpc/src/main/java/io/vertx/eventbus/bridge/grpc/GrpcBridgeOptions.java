package io.vertx.eventbus.bridge.grpc;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * gRPC bridge options.
 */
@DataObject
public class GrpcBridgeOptions extends BridgeOptions {

  private Duration replyTimeout = Duration.of(DeliveryOptions.DEFAULT_TIMEOUT, ChronoUnit.SECONDS);

  /**
   * @return the reply timeout
   */
  public Duration getReplyTimeout() {
    return replyTimeout;
  }

  /**
   * <p>The value at which the bridge considers a reply from bridge clients to not happen. When the timeout fires
   * the bridge fails the pending reply.</p>
   *
   * <p>Explanation: an event-bus reply handler has an implicit timeout, unfortunately this timeout value is not
   * propagated, as such the bridge needs such timeout value. This value is equivalent to {@link DeliveryOptions#getSendTimeout()}</p>
   *
   * @param replyTimeout the timeout
   * @return the reference to this object
   */
  public GrpcBridgeOptions setReplyTimeout(Duration replyTimeout) {
    this.replyTimeout = Objects.requireNonNull(replyTimeout);
    return this;
  }

  @Override
  public GrpcBridgeOptions addInboundPermitted(PermittedOptions permitted) {
    return (GrpcBridgeOptions) super.addInboundPermitted(permitted);
  }

  @Override
  public GrpcBridgeOptions setInboundPermitteds(List<PermittedOptions> inboundPermitted) {
    return (GrpcBridgeOptions) super.setInboundPermitteds(inboundPermitted);
  }

  @Override
  public GrpcBridgeOptions addOutboundPermitted(PermittedOptions permitted) {
    return (GrpcBridgeOptions) super.addOutboundPermitted(permitted);
  }

  @Override
  public GrpcBridgeOptions setOutboundPermitteds(List<PermittedOptions> outboundPermitted) {
    return (GrpcBridgeOptions) super.setOutboundPermitteds(outboundPermitted);
  }
}
