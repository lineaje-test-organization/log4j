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
package org.apache.logging.log4j.core.filter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginAttribute;
import org.apache.logging.log4j.plugins.PluginFactory;
import org.apache.logging.log4j.util.PerformanceSensitive;

/**
 * This filter returns the onMatch result if the marker in the LogEvent is the same as or has the
 * configured marker as a parent.
 */
@Configurable(elementType = Filter.ELEMENT_TYPE, printObject = true)
@Plugin
@PerformanceSensitive("allocation")
public final class MarkerFilter extends AbstractFilter {

    private final String name;

    private MarkerFilter(final String name, final Result onMatch, final Result onMismatch) {
        super(onMatch, onMismatch);
        this.name = name;
    }

    @Override
    public Result filter(
            final Logger logger, final Level level, final Marker marker, final String msg, final Object... params) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger, final Level level, final Marker marker, final Object msg, final Throwable t) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger, final Level level, final Marker marker, final Message msg, final Throwable t) {
        return filter(marker);
    }

    @Override
    public Result filter(final LogEvent event) {
        return filter(event.getMarker());
    }

    private Result filter(final Marker marker) {
        return marker != null && marker.isInstanceOf(name) ? onMatch : onMismatch;
    }

    @Override
    public Result filter(
            final Logger logger, final Level level, final Marker marker, final String msg, final Object p0) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2,
            final Object p3) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2,
            final Object p3,
            final Object p4) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2,
            final Object p3,
            final Object p4,
            final Object p5) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2,
            final Object p3,
            final Object p4,
            final Object p5,
            final Object p6) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2,
            final Object p3,
            final Object p4,
            final Object p5,
            final Object p6,
            final Object p7) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2,
            final Object p3,
            final Object p4,
            final Object p5,
            final Object p6,
            final Object p7,
            final Object p8) {
        return filter(marker);
    }

    @Override
    public Result filter(
            final Logger logger,
            final Level level,
            final Marker marker,
            final String msg,
            final Object p0,
            final Object p1,
            final Object p2,
            final Object p3,
            final Object p4,
            final Object p5,
            final Object p6,
            final Object p7,
            final Object p8,
            final Object p9) {
        return filter(marker);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Creates the MarkerFilter.
     * @param marker The Marker name to match.
     * @param onMatch The action to take if a match occurs.
     * @param onMismatch The action to take if no match occurs.
     * @return A MarkerFilter.
     */
    // TODO Consider refactoring to use AbstractFilter.AbstractFilterBuilder
    @PluginFactory
    public static MarkerFilter createFilter(
            @PluginAttribute final String marker,
            @PluginAttribute final Result onMatch,
            @PluginAttribute final Result onMismatch) {

        if (marker == null) {
            LOGGER.error("A marker must be provided for MarkerFilter");
            return null;
        }
        return new MarkerFilter(marker, onMatch, onMismatch);
    }
}
