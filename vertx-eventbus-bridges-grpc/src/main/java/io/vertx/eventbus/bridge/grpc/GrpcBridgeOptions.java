package io.vertx.eventbus.bridge.grpc;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

@DataObject
public class GrpcBridgeOptions extends BridgeOptions {

  private Duration replyTimeout = Duration.of(DeliveryOptions.DEFAULT_TIMEOUT, ChronoUnit.SECONDS);

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  public Duration getReplyTimeout() {
    return replyTimeout;
  }

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  public GrpcBridgeOptions setReplyTimeout(Duration replyTimeout) {
    this.replyTimeout = replyTimeout;
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
