package org.apache.cloudberry.pxf.automation;

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

import org.apache.commons.lang.StringUtils;
import org.apache.cloudberry.pxf.automation.structures.tables.basic.Table;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared test-data fixtures for Testcontainers-based tests.
 *
 * Mirrors the data layout of {@link BaseFunctionality}
 */
public class SmallDataFactory {

    /**
     * Create a data table with {@code numRows} rows of small data,
     * with the following fields: String, int, double, long and boolean.
     *
     * @param uniqueName prefix applied to the {@code name} column (empty for no prefix)
     * @param numRows    number of rows to generate
     * @return the generated {@link Table}
     */
    public Table getSmallData(String uniqueName, int numRows) {
        List<List<String>> data = new ArrayList<>();

        for (int i = 1; i <= numRows; i++) {
            List<String> row = new ArrayList<>();
            row.add(String.format("%s%srow_%d", uniqueName, StringUtils.isBlank(uniqueName) ? "" : "_", i));
            row.add(String.valueOf(i));
            row.add(Double.toString(i));
            row.add(Long.toString(100000000000L * i));
            row.add(String.valueOf(i % 2 == 0));
            data.add(row);
        }

        Table dataTable = new Table("dataTable", null);
        dataTable.setData(data);

        return dataTable;
    }

    public Table getSmallData() {
        return getSmallData("");
    }

    public Table getSmallData(String uniqueName) {
        return getSmallData(uniqueName, 100);
    }

    /**
     * Serialize a table to a temporary CSV file, rows separated by newlines and
     * fields separated by {@code delimiter}, with no trailing newline. The caller
     * owns the returned file and is responsible for deleting it.
     *
     * @param table     the table to serialize
     * @param delimiter the field delimiter
     * @return path to the temporary CSV file
     */
    public Path writeTableToCsv(Table table, String delimiter) throws IOException {
        Path tempFile = Files.createTempFile("tc-fixture-", ".csv");
        List<List<String>> data = table.getData();
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            for (int i = 0; i < data.size(); i++) {
                writer.append(String.join(delimiter, data.get(i)));
                if (i != data.size() - 1) {
                    writer.newLine();
                }
            }
        }
        return tempFile;
    }
}
