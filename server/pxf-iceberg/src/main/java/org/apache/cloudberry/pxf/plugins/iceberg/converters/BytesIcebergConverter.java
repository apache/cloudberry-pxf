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
import org.apache.cloudberry.pxf.api.error.PxfRuntimeException;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

import java.nio.ByteBuffer;

/**
 * Converts bytea values to and from Iceberg binary and fixed values. Iceberg's generic records hold a
 * binary value as a ByteBuffer and a fixed one as a byte[], while PXF sends a byte[] to the database
 * and gets a byte[] (external tables) or a ByteBuffer (pxf_fdw) from it.
 */
@Getter
public class BytesIcebergConverter implements IcebergConverter<Object> {

    private final Type icebergType;
    private final DataType cloudberryType = DataType.BYTEA;

    public BytesIcebergConverter(Type icebergType) {
        if (icebergType.typeId() != Type.TypeID.BINARY && icebergType.typeId() != Type.TypeID.FIXED) {
            throw new IllegalArgumentException("Not a binary or fixed type: " + icebergType);
        }
        this.icebergType = icebergType;
    }

    @Override
    public Object convertFromCloudberryToIceberg(Object input) {
        if (input == null) {
            return null;
        }
        byte[] bytes = toBytes(input);
        if (icebergType instanceof Types.FixedType fixedType) {
            if (bytes.length != fixedType.length()) {
                throw new PxfRuntimeException(String.format("a value of %d bytes does not fit into an Iceberg %s column",
                        bytes.length, fixedType));
            }
            return bytes;
        }
        return ByteBuffer.wrap(bytes);
    }

    @Override
    public Object convertFromIcebergToCloudberry(Object input) {
        return input != null ? toBytes(input) : null;
    }

    private static byte[] toBytes(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof ByteBuffer buffer) {
            // copy the remaining bytes without moving the position of the caller's buffer
            byte[] bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);
            return bytes;
        }
        throw new PxfRuntimeException(String.format("Unexpected value of type %s for a bytea column", value.getClass().getName()));
    }
}
