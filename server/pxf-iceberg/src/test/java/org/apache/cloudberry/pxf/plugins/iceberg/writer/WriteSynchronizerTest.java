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

import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WriteSynchronizerTest {

    private static final DataFile FILE_0 = dataFile("file-0");
    private static final DataFile FILE_1 = dataFile("file-1");

    @Test
    public void testLastSegmentGetsAllFiles() throws Exception {
        WriteSynchronizer synchronizer = new WriteSynchronizer("tx");
        assertTrue(synchronizer.open(0));
        assertTrue(synchronizer.open(1));

        assertTrue(synchronizer.saveAndGetBatchIfCompleted(0, () -> List.of(FILE_0)).isEmpty());
        WriteSynchronizer.Batch batch = synchronizer.saveAndGetBatchIfCompleted(1, () -> List.of(FILE_1)).orElseThrow();

        assertFalse(batch.failed());
        assertEquals(2, batch.files().size());
        assertTrue(batch.files().containsAll(List.of(FILE_0, FILE_1)));
        assertFalse(synchronizer.isInUse());
    }

    @Test
    public void testFailedSegmentReleasesItsPermit() throws Exception {
        WriteSynchronizer synchronizer = new WriteSynchronizer("tx");
        assertTrue(synchronizer.open(0));
        assertTrue(synchronizer.open(1));

        assertThrows(IOException.class, () -> synchronizer.saveAndGetBatchIfCompleted(0, () -> {
            throw new IOException("failed to close the writer");
        }));

        // the other segment still completes, but its files must not be committed
        WriteSynchronizer.Batch batch = synchronizer.saveAndGetBatchIfCompleted(1, () -> List.of(FILE_1)).orElseThrow();
        assertTrue(batch.failed());
        assertEquals(List.of(FILE_1), List.copyOf(batch.files()));
        assertFalse(synchronizer.isInUse());
    }

    @Test
    public void testAbortedSegmentFailsTheBatch() throws Exception {
        WriteSynchronizer synchronizer = new WriteSynchronizer("tx");
        assertTrue(synchronizer.open(0));
        assertTrue(synchronizer.open(1));

        assertTrue(synchronizer.saveAndGetBatchIfCompleted(0, () -> List.of(FILE_0)).isEmpty());
        AtomicBoolean aborted = new AtomicBoolean();
        WriteSynchronizer.Batch batch = synchronizer.abortAndGetBatchIfCompleted(1, () -> aborted.set(true)).orElseThrow();

        assertTrue(aborted.get());
        assertTrue(batch.failed());
        assertEquals(List.of(FILE_0), List.copyOf(batch.files()));
        assertFalse(synchronizer.isInUse());
    }

    @Test
    public void testNextWriteIsNotFailedByThePreviousOne() throws Exception {
        WriteSynchronizer synchronizer = new WriteSynchronizer("tx");
        assertTrue(synchronizer.open(0));
        assertTrue(synchronizer.abortAndGetBatchIfCompleted(0, () -> {}).orElseThrow().failed());

        assertTrue(synchronizer.open(0));
        WriteSynchronizer.Batch batch = synchronizer.saveAndGetBatchIfCompleted(0, () -> List.of(FILE_0)).orElseThrow();
        assertFalse(batch.failed());
        assertEquals(List.of(FILE_0), List.copyOf(batch.files()));
    }

    private static DataFile dataFile(String path) {
        return DataFiles.builder(PartitionSpec.unpartitioned())
                .withPath(path)
                .withFormat(FileFormat.PARQUET)
                .withFileSizeInBytes(1)
                .withRecordCount(1)
                .build();
    }
}
