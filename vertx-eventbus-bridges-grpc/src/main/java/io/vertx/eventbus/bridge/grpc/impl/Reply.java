package io.vertx.eventbus.bridge.grpc.impl;

import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;

class Reply {

  final ContextInternal context;
  final String address;
  final Message<?> request;
  final long timeoutID;

  Reply(ContextInternal context, String address, Message<?> request, long timeoutID) {
    this.context = context;
    this.address = address;
    this.request = request;
    this.timeoutID = timeoutID;
  }
}
