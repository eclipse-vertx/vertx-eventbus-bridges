package io.vertx.eventbus.bridge.grpc.impl;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReplyManager extends ConcurrentHashMap<String, Reply> {

  private final long timeoutMS;

  public ReplyManager(long timeoutMS) {
    this.timeoutMS = timeoutMS;
  }

  public String createReply(Message<?> reply) {
    reply.headers();
    String replyAddress = UUID.randomUUID().toString();
    ContextInternal context = ContextInternal.current();
    long timeoutID = context.setTimer(timeoutMS, id_ -> {
      remove(replyAddress);
      reply.fail(0, "Reply timeout");
    });
    put(replyAddress, new Reply(context, replyAddress, reply, timeoutID));
    return replyAddress;
  }

  public boolean tryReply(String address, Object body, DeliveryOptions deliveryOptions) {
    Reply reply = remove(address);
    boolean isReply = reply != null;
    if (isReply) {
      if (reply.context.owner().cancelTimer(reply.timeoutID)) {
        reply.request.reply(body, deliveryOptions);
      }
    }
    return isReply;
  }
}
