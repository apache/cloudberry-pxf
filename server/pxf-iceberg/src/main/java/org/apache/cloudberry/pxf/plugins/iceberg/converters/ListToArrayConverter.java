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

package org.apache.cloudberry.pxf.plugins.iceberg.converters;

import lombok.Getter;
import org.apache.cloudberry.pxf.api.GreenplumDateTime;
import org.apache.cloudberry.pxf.api.error.UnsupportedTypeException;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.iceberg.types.Type;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads an Iceberg list of primitive values into a Postgres array column, in the array's external text
 * representation that PXF sends to the database for array types. Each element goes through the converter
 * of the array's element type, so the elements are checked and converted like values of a scalar column.
 */
@Getter
public class ListToArrayConverter implements IcebergConverter<Object> {

    private final Type icebergType;
    private final DataType cloudberryType;
    private final IcebergConverter<?> elementConverter;

    public ListToArrayConverter(Type icebergType, DataType cloudberryType, IcebergConverter<?> elementConverter) {
        this.icebergType = icebergType;
        this.cloudberryType = cloudberryType;
        this.elementConverter = elementConverter;
    }

    @Override
    public Object convertFromCloudberryToIceberg(Object input) {
        throw new UnsupportedTypeException(String.format("Writing %s values to an Iceberg %s column is not supported",
                cloudberryType, icebergType));
    }

    @Override
    public Object convertFromIcebergToCloudberry(Object input) {
        if (input == null) {
            return null;
        }
        StringBuilder array = new StringBuilder("{");
        boolean first = true;
        for (Object element : (List<?>) input) {
            if (!first) {
                array.append(',');
            }
            first = false;
            appendElement(array, elementConverter.convertFromIcebergToCloudberry(element));
        }
        return array.append('}').toString();
    }

    /**
     * Appends an element in double quotes, which Postgres accepts for elements of every type, escaping
     * double quotes and backslashes; a NULL element is written unquoted.
     */
    private static void appendElement(StringBuilder array, Object value) {
        if (value == null) {
            array.append("NULL");
            return;
        }
        String text = toText(value);
        array.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                array.append('\\');
            }
            array.append(c);
        }
        array.append('"');
    }

    /**
     * Returns the text of an element the way PXF sends a value of the element type to the database.
     * Timestamps use the formatters of the pxf_fdw path, as LocalDateTime.toString() leaves out zero seconds.
     */
    private static String toText(Object value) {
        if (value instanceof byte[] bytes) {
            return "\\x" + HexFormat.of().formatHex(bytes);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(GreenplumDateTime.DATETIME_FORMATTER);
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.format(GreenplumDateTime.DATETIME_WITH_TIMEZONE_FORMATTER);
        }
        return value.toString();
    }
}
