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
package org.apache.logging.log4j.layout.template.json;

import static org.apache.logging.log4j.layout.template.json.TestHelpers.asMap;
import static org.apache.logging.log4j.layout.template.json.TestHelpers.writeJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.layout.ByteBufferDestination;
import org.apache.logging.log4j.core.layout.StringBuilderEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Tests {@link JsonTemplateLayout} doesn't exhibit unexpected behavior when accessed concurrently.
 * <p>
 * Earlier the test was designed to pass a specially crafted {@link ByteBufferDestination} implementation raising a flag when a concurrent access was detected.
 * Though this doesn't play along well with the {@link ThreadLocal}s employed in {@link StringBuilderEncoder}.
 * Those do concurrently access to {@link ByteBufferDestination} in ways with certain assumptions for the appender due to efficiency reasons.
 * Eventually we converged to the current state of the test where the output is written to a file and is checked for any interleaved lines.
 * </p>
 */
@SuppressWarnings("SameParameterValue")
// Running tests in parallel is causing strange issues at the Log4j configuration level.
// Falling back to sequential test run for the moment.
@Execution(ExecutionMode.SAME_THREAD)
class JsonTemplateLayoutConcurrentEncodeTest {

    @Test
    void test_concurrent_encode() throws IOException {
        final Path appenderFilepath = createAppenderFilepath();
        final int workerCount = 10;
        final int messageLength = 1_000;
        final int messageCount = 1_000;
        try {
            withContextFromTemplate(appenderFilepath, loggerContext -> {
                final Logger logger = loggerContext.getLogger(JsonTemplateLayoutConcurrentEncodeTest.class);
                runWorkers(workerCount, messageLength, messageCount, logger);
            });
            verifyLines(appenderFilepath, messageLength, workerCount * messageCount);
        } catch (final Throwable error) {
            final String message = String.format("test failure for appender pointing to file: `%s`", appenderFilepath);
            throw new AssertionError(message, error);
        }
        Files.delete(appenderFilepath);
    }

    private static Path createAppenderFilepath() {
        final String appenderFilename = JsonTemplateLayoutConcurrentEncodeTest.class.getSimpleName() + ".log";
        return Paths.get(System.getProperty("java.io.tmpdir"), appenderFilename);
    }

    private static void withContextFromTemplate(
            final Path appenderFilepath, final Consumer<LoggerContext> loggerContextConsumer) {

        // Create the configuration builder.
        final String configName = JsonTemplateLayoutConcurrentEncodeTest.class.getSimpleName();
        final ConfigurationBuilder<?> configBuilder = ConfigurationBuilderFactory.newConfigurationBuilder()
                .setStatusLevel(Level.ERROR)
                .setConfigurationName(configName);

        // Create the configuration.
        final Object eventTemplate = asMap("$resolver", "message");
        final String eventTemplateJson = writeJson(eventTemplate);
        final String appenderName = "File";
        final Configuration config = configBuilder
                .add(configBuilder
                        .newAppender(appenderName, "File")
                        .addAttribute(
                                "fileName", appenderFilepath.toAbsolutePath().toString())
                        .addAttribute("append", false)
                        .addAttribute("immediateFlush", false)
                        .addAttribute("ignoreExceptions", false)
                        .add(configBuilder
                                .newLayout("JsonTemplateLayout")
                                .addAttribute("eventTemplate", eventTemplateJson)))
                .add(configBuilder.newRootLogger(Level.ALL).add(configBuilder.newAppenderRef(appenderName)))
                .build(false);

        // Initialize the configuration and pass it to the consumer.
        try (final LoggerContext loggerContext = Configurator.initialize(config)) {
            loggerContextConsumer.accept(loggerContext);
        }
    }

    private static void runWorkers(
            final int workerCount, final int messageLength, final int messageCount, final Logger logger) {
        final List<Thread> workers = IntStream.range(0, workerCount)
                .mapToObj((final int threadIndex) -> createWorker(messageLength, messageCount, logger, threadIndex))
                .collect(Collectors.toList());
        workers.forEach(Thread::start);
        workers.forEach((final Thread worker) -> {
            try {
                worker.join();
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
                System.err.format("join to `%s` interrupted%n", worker.getName());
            }
        });
    }

    private static Thread createWorker(
            final int messageLength, final int messageCount, final Logger logger, final int threadIndex) {

        // Check thread index.
        final int maxThreadIndex = 'Z' - 'A';
        if (threadIndex > maxThreadIndex) {
            final String message =
                    String.format("was expecting `threadIndex <= %d`, found: %d", maxThreadIndex, threadIndex);
            throw new IndexOutOfBoundsException(message);
        }

        // Determine the message to be logged.
        final String messageLetter = String.valueOf((char) ('A' + threadIndex));
        final String message = messageLetter.repeat(messageLength);

        // Create the worker thread.
        final String threadName = String.format("Worker-%d", threadIndex);
        return new Thread(
                () -> {
                    for (int i = 0; i < messageCount; i++) {
                        logger.info(message);
                    }
                },
                threadName);
    }

    private static void verifyLines(final Path appenderFilepath, final int messageLength, final int messageCount) {
        try (final InputStream inputStream = new FileInputStream(appenderFilepath.toFile());
                final Reader reader = new InputStreamReader(inputStream, StandardCharsets.US_ASCII);
                final BufferedReader bufferedReader = new BufferedReader(reader)) {
            int lineCount = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                try {
                    verifyLine(messageLength, line);
                } catch (final Throwable error) {
                    final String message =
                            String.format("verification failure at line %d: `%s`", (lineCount + 1), line);
                    throw new AssertionError(message, error);
                }
                lineCount++;
            }
            assertThat(lineCount).isEqualTo(messageCount);
        } catch (final IOException error) {
            final String message = String.format("error verifying file: `%s`", appenderFilepath);
            throw new RuntimeException(message, error);
        }
    }

    private static void verifyLine(final int messageLength, final String line) {
        assertThat(line).hasSize(messageLength + 2);
        final char c0 = line.charAt(0);
        final char cN = line.charAt(messageLength + 1);
        assertThat(c0).isEqualTo('"').isEqualTo(cN);
        final char c1 = line.charAt(1);
        for (int i = 1; i < messageLength; i++) {
            final char c = line.charAt(1 + i);
            assertThat(c).describedAs("character at index %d", i).isEqualTo(c1);
        }
    }
}
