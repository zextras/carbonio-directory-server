// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: GPL-2.0-only

package com.zimbra.cs.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttrDocsGeneratorTest {

  @Test
  void generatesBundleWithKnownAttributeAndStaticAssets(@TempDir Path out) throws Exception {
    AttributeManager am = AttributeManager.fromResource();
    assertEquals(false, am.hasErrors(), am.getErrors());

    new AttrDocsGenerator(am).generate(out, "1.2.3", "deadbee");

    Path attrsJs = out.resolve("attrs.js");
    Path versionJs = out.resolve("version.js");
    assertTrue(Files.exists(attrsJs), "attrs.js missing");
    assertTrue(Files.exists(versionJs), "version.js missing");
    assertTrue(Files.exists(out.resolve("index.html")), "index.html missing");
    assertTrue(Files.exists(out.resolve("app.js")), "app.js missing");
    assertTrue(Files.exists(out.resolve("style.css")), "style.css missing");

    String attrs = Files.readString(attrsJs);
    assertTrue(attrs.startsWith("window.ATTRS_DATA = {"),
        "attrs.js should assign window.ATTRS_DATA to a JSON object");
    assertTrue(attrs.trim().endsWith(";"), "attrs.js should end with a statement terminator");
    assertTrue(attrs.contains("\"zimbraId\""),
        "attrs.js should include the well-known zimbraId attribute");
    assertTrue(attrs.contains("\"objectClasses\""),
        "attrs.js should include the objectClasses block");

    String version = Files.readString(versionJs);
    assertTrue(version.startsWith("window.VERSION_DATA = {"),
        "version.js should assign window.VERSION_DATA to a JSON object");
    assertTrue(version.contains("\"version\":\"1.2.3\""), "version string not propagated");
    assertTrue(version.contains("\"commit\":\"deadbee\""), "commit string not propagated");
  }
}
