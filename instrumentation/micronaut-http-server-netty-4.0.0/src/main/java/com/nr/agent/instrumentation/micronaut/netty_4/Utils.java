/*
 *
 *  * Copyright 2025 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.nr.agent.instrumentation.micronaut.netty_4;

import io.micronaut.web.router.RouteMatch;

import java.util.Map;

public class Utils {

    public static void decorateWithRoute(RouteMatch<?> routeMatch) {
    }

    public static void addAttribute(Map<String, Object> attributes, String key, Object value) {
        if (attributes != null && key != null && !key.isEmpty() && value != null) {
            attributes.put(key, value);
        }
    }
}
