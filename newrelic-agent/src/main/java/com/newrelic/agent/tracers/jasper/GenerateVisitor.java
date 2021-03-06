/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.tracers.jasper;

// a proxy to org/apache/jasper/compiler/Generator$GenerateVisitor
public interface GenerateVisitor extends Visitor {
    void visit(TemplateText text) throws Exception;
}
