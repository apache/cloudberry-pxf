package org.apache.cloudberry.pxf.automation.features.cloud;

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

import org.apache.cloudberry.pxf.automation.structures.tables.basic.Table;
import org.apache.cloudberry.pxf.automation.testcontainers.MinIOContainer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Uploads small CSV test data to MinIO using the same row layout as {@code BaseFunctionality#getSmallData()}.
 */
public final class CloudAccessDataUploader {

    private CloudAccessDataUploader() {
    }

    public static Table buildSmallData() {
        return buildSmallData("", 100);
    }

    public static Table buildSmallData(String uniqueName, int numRows) {
        List<List<String>> data = new ArrayList<>();

        for (int i = 1; i <= numRows; i++) {
            List<String> row = new ArrayList<>();
            String prefix = uniqueName == null || uniqueName.isEmpty() ? "" : uniqueName + "_";
            row.add(String.format("%srow_%d", prefix, i));
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

    public static void uploadSmallData(MinIOContainer minio, String bucket, String objectKey, Table dataTable,
                                       String delimiter) throws IOException {
        Path tempFile = Files.createTempFile("cloudaccess-", ".csv");
        try {
            writeTableToCsv(tempFile, dataTable, delimiter);
            System.out.println("[CloudAccessDataUploader] Uploading " + tempFile + " -> s3://" + bucket + "/" + objectKey);
            minio.putObject(bucket, objectKey, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void writeTableToCsv(Path path, Table dataTable, String delimiter) throws IOException {
        List<List<String>> data = dataTable.getData();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < data.size(); i++) {
                List<String> row = data.get(i);
                StringBuilder line = new StringBuilder();
                for (int j = 0; j < row.size(); j++) {
                    line.append(row.get(j));
                    if (j != row.size() - 1) {
                        line.append(delimiter);
                    }
                }
                writer.append(line.toString());
                if (i != data.size() - 1) {
                    writer.newLine();
                }
            }
        }
    }
}
