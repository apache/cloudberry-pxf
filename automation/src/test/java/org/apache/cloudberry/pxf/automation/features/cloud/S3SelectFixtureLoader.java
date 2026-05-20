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

import org.apache.cloudberry.pxf.automation.testcontainers.MinIOContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Uploads committed S3 Select fixture files from src/test/resources/data/s3select/ into MinIO.
public final class S3SelectFixtureLoader {

    private static final String[] FIXTURE_FILES = {
            "sample.csv",
            "sample-no-header.csv",
            "sample.csv.gz",
            "sample.csv.bz2",
            "sample.parquet",
            "sample.snappy.parquet",
            "sample.gz.parquet"
    };

    private static final String DATA_SUBDIR = "s3select";

    private S3SelectFixtureLoader() {
    }

    // Uploads all fixture files under objectKeyPrefix in the given bucket
    // (e.g. tmp/pxf_automation_data/<uuid>/s3select/).
    public static void uploadAll(MinIOContainer minio, String bucket, String objectKeyPrefix) throws IOException {
        Path fixturesDir = resolveFixturesDirectory();
        String prefix = objectKeyPrefix.endsWith("/") ? objectKeyPrefix : objectKeyPrefix + "/";

        for (String filename : FIXTURE_FILES) {
            Path localFile = fixturesDir.resolve(filename);
            if (!Files.isRegularFile(localFile)) {
                throw new IOException("Missing S3 Select fixture: " + localFile);
            }
            String key = prefix + filename;
            System.out.println("[S3SelectFixtureLoader] Uploading " + localFile + " -> s3://" + bucket + "/" + key);
            minio.putObject(bucket, key, localFile);
        }
    }

    public static void deletePrefix(MinIOContainer minio, String bucket, String objectKeyPrefix) {
        String prefix = objectKeyPrefix.endsWith("/") ? objectKeyPrefix : objectKeyPrefix + "/";
        minio.deletePrefix(bucket, prefix);
    }

    private static Path resolveFixturesDirectory() throws IOException {
        Path relative = Paths.get("src/test/resources/data", DATA_SUBDIR);
        if (Files.isDirectory(relative)) {
            return relative.toAbsolutePath().normalize();
        }

        Path fromRepo = findRepoRoot().resolve("automation/src/test/resources/data").resolve(DATA_SUBDIR);
        if (Files.isDirectory(fromRepo)) {
            return fromRepo;
        }

        throw new IOException("Cannot find s3select fixtures directory (tried "
                + relative.toAbsolutePath() + " and " + fromRepo + ")");
    }

    private static Path findRepoRoot() throws IOException {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6; i++) {
            if (new File(dir, "automation/pom.xml").exists()) {
                return dir.toPath().toAbsolutePath().normalize();
            }
            dir = dir.getParentFile();
            if (dir == null) {
                break;
            }
        }
        throw new IOException("Cannot auto-detect cloudberry-pxf repo root from user.dir="
                + System.getProperty("user.dir"));
    }
}
