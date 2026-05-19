/*
 * SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.zextras.entry;

import com.zimbra.cs.ephemeral.EphemeralInput.Expiration;
import com.zimbra.cs.ephemeral.EphemeralResult;
import java.util.Base64;
import java.util.Date;
import java.util.Map;



/**
 * Minimal attribute-access contract for LDAP-backed entries.
 *
 * <p>Captures the public surface that generated ZAttr classes need from their
 * host entry. Implementors (e.g. {@code com.zimbra.cs.account.Entry} in the
 * legacy {@code com.zimbra} namespace) may expose a wider API; this interface
 * is intentionally narrow so it can live in a tooling module without dragging
 * in mailbox runtime dependencies.
 */
public interface IEntry<X extends IServiceException> {

  String getAttr(String name, String defaultValue);
  String getAttr(String name, boolean applyDefaults, boolean skipEphemeralCheck);
  String getAttr(String name, String defaultValue, boolean skipEphemeralCheck);

  boolean getBooleanAttr(String name, boolean defaultValue);
  boolean getBooleanAttr(String name, boolean defaultValue, boolean applyDefaults);

  int getIntAttr(String name, int defaultValue);
  int getIntAttr(String name, int defaultValue, boolean applyDefaults);

  long getLongAttr(String name, long defaultValue);
  long getLongAttr(String name, long defaultValue, boolean applyDefaults);

  byte[] getBinaryAttr(String name);
  byte[] getBinaryAttr(String name, boolean applyDefaults);

  long getTimeInterval(String name, long defaultValue);
  long getTimeInterval(String name, long defaultValue, boolean applyDefaults);

  Date getGeneralizedTimeAttr(String name, Date defaultValue);
  Date getGeneralizedTimeAttr(String name, Date defaultValue, boolean applyDefaults);

  String toGeneralizedTime(Date date);

  default String encodeLDAPBase64(byte[] data) {
    return Base64.getEncoder().encodeToString(data);
  }

  default byte[] decodeLDAPBase64(String data) {
    return Base64.getDecoder().decode(data);
  }

  default void addToMultiMap(Map<String, Object> result, String name, String value) {
    Object currentValue = result.get(name);
    if (currentValue == null) {
      result.put(name, value);
    } else if (currentValue instanceof String) {
      result.put(name, new String[] { (String) currentValue, value });
    } else if (currentValue instanceof String[]) {
      String[] ov = (String[]) currentValue;
      String[] nv = new String[ov.length + 1];
      System.arraycopy(ov, 0, nv, 0, ov.length);
      nv[ov.length] = value;
      result.put(name, nv);
    }
  }

  Date parseGeneralizedTime(String time);

  String[] getMultiAttr(String name, boolean applyDefaults);
  String[] getMultiAttr(String name, boolean applyDefaults, boolean skipEphemeralCheck);

  EphemeralResult getEphemeralAttr(String key, String dynamicComponent) throws X;
  void modifyEphemeralAttr(String key, String dynamicComponent, String value, boolean update, Expiration expiration) throws X;
  void deleteEphemeralAttr(String key) throws X;
  void deleteEphemeralAttr(String key, String dynamicComponent, String value) throws X;
  void purgeEphemeralAttr(String key) throws X;
  boolean hasEphemeralAttr(String key, String dynamicComponent) throws X;
}
