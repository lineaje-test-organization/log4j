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
package org.apache.logging.log4j.status;

import static org.apache.logging.log4j.spi.LoggingSystemProperty.STATUS_DATE_FORMAT;
import static org.apache.logging.log4j.spi.LoggingSystemProperty.STATUS_DEFAULT_LISTENER_LEVEL;
import static org.apache.logging.log4j.spi.LoggingSystemProperty.STATUS_LOGGER_DEBUG;
import static org.apache.logging.log4j.spi.LoggingSystemProperty.STATUS_MAX_ENTRIES;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.util.PropertyEnvironment;

public class StatusLoggerConfiguration {
    private final PropertyEnvironment environment;

    public StatusLoggerConfiguration(final PropertyEnvironment environment) {
        this.environment = environment;
    }

    public int getMaxEntries() {
        return environment.getIntegerProperty(STATUS_MAX_ENTRIES, 200);
    }

    public Level getDefaultLevel() {
        return Level.toLevel(environment.getStringProperty(STATUS_DEFAULT_LISTENER_LEVEL), Level.WARN);
    }

    public boolean isDebugEnabled() {
        return environment.getBooleanProperty(STATUS_LOGGER_DEBUG, false, true);
    }

    public DateFormat getDateTimeFormat() {
        final String format = environment.getStringProperty(STATUS_DATE_FORMAT);
        if (format != null) {
            try {
                return new SimpleDateFormat(format);
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
