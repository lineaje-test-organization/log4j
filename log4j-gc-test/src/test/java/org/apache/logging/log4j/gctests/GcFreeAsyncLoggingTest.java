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
package org.apache.logging.log4j.gctests;

import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector;
import org.apache.logging.log4j.core.impl.Log4jPropertyKey;
import org.apache.logging.log4j.spi.LoggingSystemProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies steady state logging is GC-free.
 *
 * @see <a href="https://github.com/google/allocation-instrumenter">Google Allocation Instrumenter</a>
 */
@Tag("allocation")
@Tag("functional")
public class GcFreeAsyncLoggingTest {

    @Test
    public void testNoAllocationDuringSteadyStateLogging() throws Throwable {
        GcFreeLoggingTestUtil.runTest(getClass());
    }

    /**
     * This code runs in a separate process, instrumented with the Google Allocation Instrumenter.
     */
    public static void main(final String[] args) throws Exception {
        System.setProperty(LoggingSystemProperty.THREAD_CONTEXT_GARBAGE_FREE_ENABLED.getSystemKey(), "true");
        System.setProperty(
                Log4jPropertyKey.ASYNC_LOGGER_RING_BUFFER_SIZE.getSystemKey(), "128"); // minimum ringbuffer size
        System.setProperty(
                Log4jPropertyKey.CONTEXT_SELECTOR_CLASS_NAME.getSystemKey(),
                AsyncLoggerContextSelector.class.getName());
        GcFreeLoggingTestUtil.executeLogging("gcFreeLogging.xml", GcFreeAsyncLoggingTest.class);
    }
}
