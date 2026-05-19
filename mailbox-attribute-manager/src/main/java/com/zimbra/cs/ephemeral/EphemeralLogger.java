// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: GPL-2.0-only

package com.zimbra.cs.ephemeral;

/**
 * Logging hook used by {@link EphemeralResult} to report value-parse failures
 * (e.g. a stored string that cannot be coerced to an int/long/boolean).
 *
 * <p>Defined in this module so {@code EphemeralResult} does not pull in a
 * concrete logging framework. Callers in mailbox-store typically inject
 * {@code ZimbraLog.ephemeral::warn}.
 */
@FunctionalInterface
public interface EphemeralLogger {
  void warn(String format, Object... args);
}
