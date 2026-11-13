/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudberry.pxf.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.apache.cloudberry.pxf.api.GreenplumDateTime.DATETIME_WITH_TIMEZONE_FORMATTER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GreenplumDateTimeTest {

    @Test
    public void parseOffsetWithHoursOnly() {
        OffsetDateTime value = OffsetDateTime.parse("2000-01-01 08:00:00+08", DATETIME_WITH_TIMEZONE_FORMATTER);
        assertEquals(ZoneOffset.ofHours(8), value.getOffset());
        assertEquals(Instant.parse("2000-01-01T00:00:00Z"), value.toInstant());
    }

    @Test
    public void parseOffsetWithMinutes() {
        OffsetDateTime value = OffsetDateTime.parse("2000-01-01 05:30:00.123456+05:30", DATETIME_WITH_TIMEZONE_FORMATTER);
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), value.getOffset());
        assertEquals(Instant.parse("2000-01-01T00:00:00.123456Z"), value.toInstant());
    }

    @Test
    public void parseOffsetWithSeconds() {
        // Greenplum prints historical offsets with seconds, e.g. LMT of Asia/Shanghai
        OffsetDateTime value = OffsetDateTime.parse("1582-10-15 20:05:43+08:05:43", DATETIME_WITH_TIMEZONE_FORMATTER);
        assertEquals(ZoneOffset.ofHoursMinutesSeconds(8, 5, 43), value.getOffset());
        assertEquals(Instant.parse("1582-10-15T12:00:00Z"), value.toInstant());
    }

    @Test
    public void parseNegativeOffsetWithSeconds() {
        OffsetDateTime value = OffsetDateTime.parse("1800-01-01 00:00:00-07:52:58", DATETIME_WITH_TIMEZONE_FORMATTER);
        assertEquals(ZoneOffset.ofHoursMinutesSeconds(-7, -52, -58), value.getOffset());
    }

    @Test
    public void formatKeepsOffsetSeconds() {
        String value = Instant.parse("1582-10-15T12:00:00Z").atZone(ZoneId.of("Asia/Shanghai"))
                .format(DATETIME_WITH_TIMEZONE_FORMATTER);
        assertEquals("1582-10-15 20:05:43+08:05:43", value);
    }

    @Test
    public void formatOmitsZeroMinutesAndSeconds() {
        String value = Instant.parse("2000-01-01T00:00:00Z").atZone(ZoneId.of("Asia/Shanghai"))
                .format(DATETIME_WITH_TIMEZONE_FORMATTER);
        assertEquals("2000-01-01 08:00:00+08", value);
    }
}
