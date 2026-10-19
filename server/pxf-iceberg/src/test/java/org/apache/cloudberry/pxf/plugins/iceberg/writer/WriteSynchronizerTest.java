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

package org.apache.cloudberry.pxf.plugins.iceberg.writer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WriteSynchronizerTest {

    private static final FileToCommit FILE_0 = new FileToCommit("file-0", 1, 1, null);
    private static final FileToCommit FILE_1 = new FileToCommit("file-1", 1, 1, null);

    @Test
    public void testLastSegmentGetsAllFiles() throws Exception {
        WriteSynchronizer synchronizer = new WriteSynchronizer("tx");
        assertTrue(synchronizer.open(0));
        assertTrue(synchronizer.open(1));

        assertEquals(List.of(), synchronizer.saveAndGetFullListIfCompleted(0, () -> List.of(FILE_0)));
        Collection<FileToCommit> files = synchronizer.saveAndGetFullListIfCompleted(1, () -> List.of(FILE_1));

        assertEquals(2, files.size());
        assertTrue(files.containsAll(List.of(FILE_0, FILE_1)));
        assertFalse(synchronizer.isInUse());
    }

    @Test
    public void testFailedSegmentReleasesItsPermit() throws Exception {
        WriteSynchronizer synchronizer = new WriteSynchronizer("tx");
        assertTrue(synchronizer.open(0));
        assertTrue(synchronizer.open(1));

        assertThrows(IOException.class, () -> synchronizer.saveAndGetFullListIfCompleted(0, () -> {
            throw new IOException("failed to close the writer");
        }));

        // the other segment still completes and gets its own files committed
        assertEquals(List.of(FILE_1), List.copyOf(synchronizer.saveAndGetFullListIfCompleted(1, () -> List.of(FILE_1))));
        assertFalse(synchronizer.isInUse());
    }
}
