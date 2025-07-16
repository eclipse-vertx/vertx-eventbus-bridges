package io.vertx.ext.bridge.tests;

import io.vertx.ext.bridge.BridgeOptions;
import org.junit.Test;

public class LegacyTest {

  @Test
  public void testCompat() {
    // Pretty much only need to check that module-info declaring io.vertx.eventbusbridge gets the new module
    BridgeOptions options = new BridgeOptions();
  }
}
