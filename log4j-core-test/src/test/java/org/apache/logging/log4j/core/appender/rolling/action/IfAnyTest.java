/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.core.appender.rolling.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the Or composite condition.
 */
public class IfAnyTest {

    @Test
    public void test() {
        final PathCondition TRUE = new FixedCondition(true);
        final PathCondition FALSE = new FixedCondition(false);
        assertTrue(IfAny.createOrCondition(TRUE, TRUE).accept(null, null, null));
        assertTrue(IfAny.createOrCondition(FALSE, TRUE).accept(null, null, null));
        assertTrue(IfAny.createOrCondition(TRUE, FALSE).accept(null, null, null));
        assertFalse(IfAny.createOrCondition(FALSE, FALSE).accept(null, null, null));
    }

    @Test
    public void testEmptyIsFalse() {
        assertFalse(IfAny.createOrCondition().accept(null, null, null));
    }

    @Test
    public void testBeforeTreeWalk() {
        final CountingCondition counter = new CountingCondition(true);
        final IfAny or = IfAny.createOrCondition(counter, counter, counter);
        or.beforeFileTreeWalk();
        assertEquals(3, counter.getBeforeFileTreeWalkCount());
    }
}
