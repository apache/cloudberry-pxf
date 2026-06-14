package org.apache.cloudberry.pxf.automation.utils;

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

import java.io.File;
import java.nio.file.Path;

/**
 * Shared helpers for the cloudberry-pxf automation suite.
 */
public final class AutomationUtils {

    private static final String REPO_MARKER = "automation/pom.xml";
    private static final int MAX_PARENT_HOPS = 6;

    private AutomationUtils() {
    }

    /**
     * Walks upward from {@code user.dir} looking for the cloudberry-pxf repo
     * root (identified by the {@code automation/pom.xml} marker). Used by code
     * that needs to read resources from the working tree at runtime.
     *
     * @throws IllegalStateException if the marker is not found within a handful
     *     of parent directories. Set {@code -Dpxf.test.repo.path=...} to bypass.
     */
    public static Path findRepoRoot() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < MAX_PARENT_HOPS; i++) {
            if (new File(dir, REPO_MARKER).exists()) {
                return dir.toPath().toAbsolutePath().normalize();
            }
            dir = dir.getParentFile();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException(
                "Cannot auto-detect cloudberry-pxf repo root from user.dir="
                        + System.getProperty("user.dir")
                        + ". Set -Dpxf.test.repo.path=... to override.");
    }
}
