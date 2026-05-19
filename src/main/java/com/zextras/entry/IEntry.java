/*
 * SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.zextras.entry;

import java.util.Date;

/**
 * Minimal attribute-access contract for LDAP-backed entries.
 *
 * <p>Captures the public surface that generated ZAttr classes need from their
 * host entry. Implementors (e.g. {@code com.zimbra.cs.account.Entry} in the
 * legacy {@code com.zimbra} namespace) may expose a wider API; this interface
 * is intentionally narrow so it can live in a tooling module without dragging
 * in mailbox runtime dependencies.
 */
public interface IEntry {

  String getAttr(String name, String defaultValue);
  String getAttr(String name, boolean applyDefaults, boolean skipEphemeralCheck);
  String getAttr(String name, String defaultValue, boolean skipEphemeralCheck);

  boolean getBooleanAttr(String name, boolean defaultValue);
  boolean getBooleanAttr(String name, boolean defaultValue, boolean applyDefaults);

  int getIntAttr(String name, int defaultValue);
  int getIntAttr(String name, int defaultValue, boolean applyDefaults);

  long getLongAttr(String name, long defaultValue);
  long getLongAttr(String name, long defaultValue, boolean applyDefaults);

  long getTimeInterval(String name, long defaultValue);
  long getTimeInterval(String name, long defaultValue, boolean applyDefaults);

  Date getGeneralizedTimeAttr(String name, Date defaultValue);
  Date getGeneralizedTimeAttr(String name, Date defaultValue, boolean applyDefaults);

  String toGeneralizedTime(Date date);

  String[] getMultiAttr(String name, boolean applyDefaults);
}
