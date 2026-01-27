package org.apache.cloudberry.pxf.api.examples;

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

import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.io.DataType;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

/**
 * Class that defines the serialization / deserialization of one record brought
 * from the external input data.
 * <p>
 * Demo implementation of resolver that returns text format
 */
public class DemoTextResolver extends DemoResolver {

    /**
     * Read the next record
     * The record contains as many fields as defined by the DDL schema.
     *
     * @param row one record
     * @return the first column contains the entire text data
     */
    @Override
    public List<OneField> getFields(OneRow row) {
        List<OneField> output = new LinkedList<>();
        Object data = row.getData();
        output.add(new OneField(DataType.VARCHAR.getOID(), data));
        return output;
    }

    /**
     * Creates a OneRow object from the singleton list.
     *
     * @param record list of {@link OneField}
     * @return the constructed {@link OneRow}
     * @throws Exception if constructing a row from the fields failed
     */
    @Override
    public OneRow setFields(List<OneField> record) throws Exception {
        if (record == null || record.isEmpty()) {
            throw new Exception("Unexpected record format, no fields provided");
        }

        // Legacy single-field end-of-stream marker (empty byte[]), keep behavior
        if (record.size() == 1 && record.get(0).val instanceof byte[]) {
            byte[] value = (byte[]) record.get(0).val;
            if (value.length == 0) {
                return null;
            }
            // Preserve legacy single-field behavior: return the bytes as-is
            return new OneRow(value);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < record.size(); i++) {
            Object val = record.get(i).val;
            if (val instanceof byte[]) {
                sb.append(new String((byte[]) val, StandardCharsets.UTF_8));
            } else if (val != null) {
                sb.append(val.toString());
            }
            if (i < record.size() - 1) {
                sb.append(',');
            }
        }
        sb.append('\n');

        return new OneRow(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
