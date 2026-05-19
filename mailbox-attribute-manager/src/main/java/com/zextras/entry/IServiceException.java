/*
 * SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.zextras.entry;

/**
 * Checked-exception root for entry-access operations.
 *
 * <p>Lives in the directory-server module so {@link IEntry} can declare
 * {@code throws IServiceException} without depending on zm-common.
 * The concrete (mailbox) extends
 * this class, so all existing call sites that throw or catch
 * {@code ServiceException} continue to work unchanged.
 */
public class IServiceException extends Exception {

  public IServiceException(String message) {
    super(message);
  }

  public IServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  public static IServiceException INVALID_REQUEST(String message, Throwable cause) {
    return new IServiceException("invalid request: " + message, cause);
  }
}
