/*
 * SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.zextras.entry;

import java.util.Map;

/**
 * Minimal provisioning contract used by generated ZAttr classes.
 *
 * <p>Only exposes the write path ({@link #modifyAttrs}); read accessors live on
 * {@link IEntry}. Declared {@code throws Exception} so the interface remains
 * dependency-free; concrete implementations narrow the throws clause to their
 * own checked exception type.
 */
public interface IProvisioning<X extends IServiceException> {

  void modifyAttrs(IEntry<X> entry, Map<String, ? extends Object> attrs) throws X;
}
