/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.util;

import com.google.common.base.Preconditions;

public final class MoreStrings {

  /** Utility class: do not instantiate. */
  private MoreStrings() {}

  public static final boolean isEmpty(CharSequence sequence) {
    return sequence.length() == 0;
  }

  public static String withoutSuffix(String str, String suffix) {
    Preconditions.checkArgument(str.endsWith(suffix), "%s must end with %s", str, suffix);
    return str.substring(0, str.length() - suffix.length());
  }

  public static String capitalize(String str) {
    Preconditions.checkNotNull(str);
    if (!str.isEmpty()) {
      return str.substring(0, 1).toUpperCase() + str.substring(1);
    } else {
      return "";
    }
  }
}
