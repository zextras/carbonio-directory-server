// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: GPL-2.0-only

package com.zimbra.cs.account;

import com.zimbra.cs.account.AttributeManager.ObjectClassInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a self-contained static docs bundle (HTML/JS/CSS + JSON) describing every
 * attribute and objectclass defined in the {@code attrs.xml} family of files. The bundle
 * is meant to be uploaded to the Carbonio docs site per release.
 */
public class AttrDocsGenerator {

  private static final List<String> STATIC_ASSETS =
      List.of("index.html", "app.js", "style.css");

  private final AttributeManager am;

  public AttrDocsGenerator(AttributeManager am) {
    this.am = am;
  }

  public void generate(Path outputDir, String version, String commit) throws IOException {
    Files.createDirectories(outputDir);

    // Emit data as JS globals instead of JSON so the bundle works from the file:// scheme
    // (browsers block fetch() under file://). Hosted over HTTP this still works unchanged.
    try (BufferedWriter w = Files.newBufferedWriter(
        outputDir.resolve("attrs.js"), StandardCharsets.UTF_8)) {
      w.write("window.ATTRS_DATA = ");
      writeAttrsJson(w);
      w.write(";\n");
    }

    try (BufferedWriter w = Files.newBufferedWriter(
        outputDir.resolve("version.js"), StandardCharsets.UTF_8)) {
      w.write("window.VERSION_DATA = ");
      writeVersionJson(w, version, commit);
      w.write(";\n");
    }

    for (String asset : STATIC_ASSETS) {
      copyResource("/docs/" + asset, outputDir.resolve(asset));
    }
  }

  private void writeAttrsJson(BufferedWriter w) throws IOException {
    Map<String, AttributeInfo> attrs = am.getAttrs();
    Map<String, ObjectClassInfo> ocs = am.getOCs();

    List<AttributeInfo> sortedAttrs = new ArrayList<>(attrs.values());
    sortedAttrs.sort(Comparator.comparing(AttributeInfo::getName, String.CASE_INSENSITIVE_ORDER));

    List<ObjectClassInfo> sortedOcs = new ArrayList<>(ocs.values());
    sortedOcs.sort(Comparator.comparing(ObjectClassInfo::getName, String.CASE_INSENSITIVE_ORDER));

    JsonWriter j = new JsonWriter(w);
    j.beginObject();
    j.key("classes");
    j.beginArray();
    for (AttributeClass c : AttributeClass.values()) {
      j.string(c.name());
    }
    j.endArray();

    j.key("flags");
    j.beginArray();
    for (AttributeFlag f : AttributeFlag.values()) {
      j.string(f.name());
    }
    j.endArray();

    j.key("attrs");
    j.beginArray();
    for (AttributeInfo ai : sortedAttrs) {
      writeAttr(j, ai);
    }
    j.endArray();

    j.key("objectClasses");
    j.beginArray();
    for (ObjectClassInfo oc : sortedOcs) {
      writeObjectClass(j, oc);
    }
    j.endArray();
    j.endObject();
  }

  private void writeAttr(JsonWriter j, AttributeInfo ai) throws IOException {
    j.beginObject();
    j.keyString("name", ai.getName());
    j.keyNumber("id", ai.getId());
    j.keyString("type", ai.getType() != null ? ai.getType().getName() : null);
    j.keyString("cardinality",
        ai.getCardinality() != null ? ai.getCardinality().name() : null);
    j.keyBool("immutable", ai.isImmutable());
    j.keyBool("deprecated", ai.isDeprecated());

    List<AttributeFlag> flags = Arrays.stream(AttributeFlag.values())
        .filter(ai::hasFlag)
        .toList();
    j.keyStringArray("flags", enumNames(flags));
    j.keyStringArray("requiredIn", enumNames(ai.getRequiredIn()));
    j.keyStringArray("optionalIn", enumNames(ai.getOptionalIn()));
    j.keyStringArray("requiresRestart",
        ai.getRequiresRestart() == null ? List.of()
            : ai.getRequiresRestart().stream().map(e -> e.name()).toList());

    List<String> since = new ArrayList<>();
    if (ai.getSince() != null) {
      for (AttributeVersion v : ai.getSince()) {
        since.add(v.toString());
      }
    }
    j.keyStringArray("since", since);
    j.keyString("deprecatedSince",
        ai.getDeprecatedSince() != null ? ai.getDeprecatedSince().toString() : null);

    j.keyString("min", minBoundValue(ai));
    j.keyString("max", maxBoundValue(ai));

    j.keyStringArray("enumValues",
        ai.getType() == AttributeType.TYPE_ENUM && ai.getmEnumSet() != null
            ? new ArrayList<>(ai.getmEnumSet())
            : List.of());

    j.keyStringArray("globalConfigValues", nullSafe(ai.getGlobalConfigValues()));
    j.keyStringArray("defaultCosValues", nullSafe(ai.getDefaultCosValues()));
    j.keyStringArray("defaultExternalCosValues", nullSafe(ai.getDefaultExternalCosValues()));

    String desc = ai.getDescription();
    j.keyString("description", desc == null ? "" : desc.trim());

    j.endObject();
  }

  private void writeObjectClass(JsonWriter j, ObjectClassInfo oc) throws IOException {
    j.beginObject();
    j.keyString("name", oc.getName());
    j.keyNumber("id", oc.getId());
    j.keyString("type", oc.getType() != null ? oc.getType().name() : null);
    j.keyString("class",
        oc.getAttributeClass() != null ? oc.getAttributeClass().name() : null);
    j.keyStringArray("sup", nullSafe(oc.getSuperOCs()));
    j.keyString("description", oc.getDescription() == null ? "" : oc.getDescription().trim());

    Set<String> ocAttrs = am.getAttrsInClass(oc.getAttributeClass());
    List<String> attrNames = ocAttrs == null ? List.of() : new ArrayList<>(ocAttrs);
    attrNames.sort(String.CASE_INSENSITIVE_ORDER);
    j.keyStringArray("attrs", attrNames);
    j.endObject();
  }

  private void writeVersionJson(BufferedWriter w, String version, String commit)
      throws IOException {
    JsonWriter j = new JsonWriter(w);
    j.beginObject();
    j.keyString("version", version == null ? "unknown" : version);
    j.keyString("commit", commit == null ? "unknown" : commit);
    j.keyString("generatedAt", Instant.now().toString());
    j.endObject();
  }

  private static List<String> enumNames(Collection<? extends Enum<?>> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream().map(e -> e.name()).toList();
  }

  private static List<String> nullSafe(List<String> list) {
    return list == null ? List.of() : list;
  }

  private static String minBoundValue(AttributeInfo ai) {
    if (ai.getType() == AttributeType.TYPE_DURATION) {
      return ai.getmMinDuration();
    }
    long min = ai.getMin();
    if (min == Long.MIN_VALUE || min == Integer.MIN_VALUE) {
      return null;
    }
    return Long.toString(min);
  }

  private static String maxBoundValue(AttributeInfo ai) {
    if (ai.getType() == AttributeType.TYPE_DURATION) {
      return ai.getmMaxDuration();
    }
    long max = ai.getMax();
    if (max == Long.MAX_VALUE || max == Integer.MAX_VALUE) {
      return null;
    }
    return Long.toString(max);
  }

  private static void copyResource(String resourcePath, Path target) throws IOException {
    try (InputStream in = AttrDocsGenerator.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IOException("missing classpath resource: " + resourcePath);
      }
      try (OutputStream out = Files.newOutputStream(target)) {
        in.transferTo(out);
      }
    }
  }

  /** Minimal JSON writer — avoids pulling in a JSON dependency. */
  static final class JsonWriter {

    private final BufferedWriter w;
    private boolean needsComma = false;

    JsonWriter(BufferedWriter w) {
      this.w = w;
    }

    void beginObject() throws IOException {
      separator();
      w.write('{');
      needsComma = false;
    }

    void endObject() throws IOException {
      w.write('}');
      needsComma = true;
    }

    void beginArray() throws IOException {
      separator();
      w.write('[');
      needsComma = false;
    }

    void endArray() throws IOException {
      w.write(']');
      needsComma = true;
    }

    void key(String name) throws IOException {
      separator();
      writeString(name);
      w.write(':');
      needsComma = false;
    }

    void keyString(String name, String value) throws IOException {
      key(name);
      if (value == null) {
        w.write("null");
      } else {
        writeString(value);
      }
      needsComma = true;
    }

    void keyNumber(String name, long value) throws IOException {
      key(name);
      w.write(Long.toString(value));
      needsComma = true;
    }

    void keyBool(String name, boolean value) throws IOException {
      key(name);
      w.write(value ? "true" : "false");
      needsComma = true;
    }

    void keyStringArray(String name, Collection<String> values) throws IOException {
      key(name);
      w.write('[');
      boolean first = true;
      if (values != null) {
        for (String v : values) {
          if (!first) {
            w.write(',');
          }
          first = false;
          if (v == null) {
            w.write("null");
          } else {
            writeString(v);
          }
        }
      }
      w.write(']');
      needsComma = true;
    }

    void string(String value) throws IOException {
      separator();
      writeString(value);
      needsComma = true;
    }

    private void separator() throws IOException {
      if (needsComma) {
        w.write(',');
      }
    }

    private void writeString(String s) throws IOException {
      w.write('"');
      int len = s.length();
      for (int i = 0; i < len; i++) {
        char c = s.charAt(i);
        switch (c) {
          case '\\':
          case '"':
            w.write('\\');
            w.write(c);
            break;
          case '\n':
            w.write("\\n");
            break;
          case '\r':
            w.write("\\r");
            break;
          case '\t':
            w.write("\\t");
            break;
          case '\b':
            w.write("\\b");
            break;
          case '\f':
            w.write("\\f");
            break;
          default:
            if (c < 0x20) {
              w.write(String.format("\\u%04x", (int) c));
            } else {
              w.write(c);
            }
        }
      }
      w.write('"');
    }
  }
}
