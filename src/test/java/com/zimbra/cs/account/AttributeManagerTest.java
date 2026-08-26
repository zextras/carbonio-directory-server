package com.zimbra.cs.account;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributeManagerTest {

  @AfterEach
  void cleanup() {
    AttributeManager.destroy();
  }

  @Test
  void shouldLoadAllAttributesWhenUsingSingleton() throws AttributeManagerException {
    final AttributeManager attributeManager = AttributeManager.getInstance();

    assertLoadedAllAttributes(attributeManager);
  }

  @Test
  void shouldLoadAllAttributesFromResources() throws AttributeManagerException {
    final AttributeManager attributeManager = AttributeManager.fromResource();
    assertLoadedAllAttributes(attributeManager);
  }

  private void assertLoadedAllAttributes(AttributeManager attributeManager) {
    final Map<String, AttributeInfo> allAttrs = attributeManager.getAttrs();
    assertEquals(1872, allAttrs.size());
  }
}
