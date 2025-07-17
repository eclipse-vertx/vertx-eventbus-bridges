open module io.vertx.tests.eventbusbridge.common {
  requires io.vertx.core;

  // We check that this requires is sufficient to access the new module via transitivity
  requires io.vertx.eventbusbridge;

  requires junit;
  requires assertj.core;
}
