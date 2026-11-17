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

package org.apache.cloudberry.pxf.plugins.iceberg;

import io.github.resilience4j.retry.Retry;
import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.error.PxfRuntimeException;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.utilities.ColumnDescriptor;
import org.apache.cloudberry.pxf.plugins.iceberg.converters.IcebergConverters;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.AvroReaderFactory;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.IcebergFragmentMetadata;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.IcebergReader;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.OrcReaderFactory;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.ParquetReaderFactory;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.ReaderProvider;
import org.apache.cloudberry.pxf.plugins.iceberg.table.TableWrapper;
import org.apache.cloudberry.pxf.plugins.iceberg.writer.IcebergWriter;
import org.apache.cloudberry.pxf.plugins.iceberg.writer.WriterFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Files;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.types.Types;
import org.apache.orc.OrcFile;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads and writes Iceberg binary and fixed columns as bytea.
 */
public class IcebergBinaryTest {

    private static final int FIXED_LENGTH = 4;

    private static final Schema SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.IntegerType.get()),
            Types.NestedField.optional(2, "bin", Types.BinaryType.get()),
            Types.NestedField.optional(3, "fix", Types.FixedType.ofLength(FIXED_LENGTH))
    );

    // the last row has NULLs, the first one an empty binary value
    private static final int ROWS = 4;

    @TempDir
    Path tempDir;

    private Table table;
    private RequestContext context;
    private IcebergResolver resolver;

    @BeforeEach
    public void setup() {
        table = new HadoopTables(new Configuration())
                .create(SCHEMA, PartitionSpec.unpartitioned(), Map.of(), tempDir.resolve("table").toString());

        context = new RequestContext();
        context.setSegmentId(0);
        context.setTransactionId("tx-1");
        context.setGpSessionId(1);
        context.setMetadata(table.schema());
        context.getTupleDescription().add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        context.getTupleDescription().add(new ColumnDescriptor("bin", DataType.BYTEA.getOID(), 1, "bytea", null));
        context.getTupleDescription().add(new ColumnDescriptor("fix", DataType.BYTEA.getOID(), 2, "bytea", null));

        resolver = new IcebergResolver();
        resolver.setRequestContext(context);
    }

    @ParameterizedTest
    @EnumSource(value = FileFormat.class, names = {"PARQUET", "AVRO"})
    public void testReadFileFormat(FileFormat format) throws Exception {
        File file = tempDir.resolve("data." + format.name().toLowerCase()).toFile();
        FileAppender<Record> appender = new GenericAppenderFactory(SCHEMA).newAppender(Files.localOutput(file), format);
        try (appender) {
            for (int i = 0; i < ROWS; i++) {
                Record record = GenericRecord.create(SCHEMA);
                record.setField("id", i);
                // a ByteBuffer for binary and a byte[] for fixed, as Iceberg's generic readers return them
                record.setField("bin", bin(i) == null ? null : ByteBuffer.wrap(bin(i)));
                record.setField("fix", fix(i));
                appender.add(record);
            }
        }
        appendFile(file, format, appender.metrics().recordCount());

        assertRows(table.newScan());
    }

    @Test
    public void testReadOrc() throws Exception {
        File file = tempDir.resolve("data.orc").toFile();
        // Iceberg's own ORC writer needs the "nohive" flavor of orc-core, so write a plain ORC file
        TypeDescription orcSchema = TypeDescription.fromString("struct<id:int,bin:binary,fix:binary>");
        Writer writer = OrcFile.createWriter(new org.apache.hadoop.fs.Path(file.getAbsolutePath()),
                OrcFile.writerOptions(new Configuration()).setSchema(orcSchema));
        try (writer) {
            VectorizedRowBatch batch = orcSchema.createRowBatch(ROWS);
            for (int i = 0; i < ROWS; i++) {
                ((LongColumnVector) batch.cols[0]).vector[i] = i;
                setBytes((BytesColumnVector) batch.cols[1], i, bin(i));
                setBytes((BytesColumnVector) batch.cols[2], i, fix(i));
            }
            batch.size = ROWS;
            writer.addRowBatch(batch);
        }
        appendFile(file, FileFormat.ORC, ROWS);

        assertRows(table.newScan());
    }

    @Test
    public void testWriteAndReadBack() throws Exception {
        TableWrapper tableWrapper = new TableWrapper(Retry.ofDefaults("test"), table);
        IcebergWriter writer = new WriterFactory().create(tableWrapper, context, null);
        for (int i = 0; i < ROWS; i++) {
            writer.write((Record) resolver.setFields(fields(i)).getData());
        }
        tableWrapper.appendFiles(null, writer.completeAndGetFilesToCommit());

        assertRows(table.newScan());
    }

    @Test
    public void testWriteConvertsToIcebergTypes() {
        Record record = (Record) resolver.setFields(fields(1)).getData();

        assertEquals(ByteBuffer.wrap(bin(1)), record.getField("bin"));
        assertArrayEquals(fix(1), (byte[]) record.getField("fix"));
    }

    @Test
    public void testWriteFromByteBuffers() {
        // pxf_fdw sends bytea values as text, which TextRecordReader parses into ByteBuffers
        ByteBuffer bin = ByteBuffer.wrap(bin(2));
        Record record = (Record) resolver.setFields(Arrays.asList(
                new OneField(DataType.INTEGER.getOID(), 2),
                new OneField(DataType.BYTEA.getOID(), bin),
                new OneField(DataType.BYTEA.getOID(), ByteBuffer.wrap(fix(2))))).getData();

        assertEquals(ByteBuffer.wrap(bin(2)), record.getField("bin"));
        assertArrayEquals(fix(2), (byte[]) record.getField("fix"));
        assertEquals(0, bin.position());
    }

    @Test
    public void testFiltersOnByteaAreNotPushedDown() {
        assertFalse(IcebergConverters.getCloudberrySupportedTypes().contains(DataType.BYTEA));
    }

    @Test
    public void testWriteFixedOfWrongLength() {
        List<OneField> row = new ArrayList<>(fields(1));
        row.set(2, new OneField(DataType.BYTEA.getOID(), new byte[]{1, 2, 3}));

        PxfRuntimeException e = assertThrows(PxfRuntimeException.class, () -> resolver.setFields(row));
        assertTrue(e.getMessage().contains("a value of 3 bytes does not fit into an Iceberg fixed[4] column"), e.getMessage());
    }

    private void appendFile(File file, FileFormat format, long recordCount) {
        DataFile dataFile = DataFiles.builder(PartitionSpec.unpartitioned())
                .withPath(file.getAbsolutePath())
                .withFormat(format)
                .withRecordCount(recordCount)
                .withFileSizeInBytes(file.length())
                .build();
        table.newAppend().appendFile(dataFile).commit();
    }

    private void assertRows(TableScan scan) throws Exception {
        ReaderProvider readerProvider = new ReaderProvider(
                List.of(new ParquetReaderFactory(), new OrcReaderFactory(), new AvroReaderFactory()));
        List<List<OneField>> rows = new ArrayList<>();
        try (CloseableIterable<FileScanTask> tasks = scan.planFiles()) {
            for (FileScanTask task : tasks) {
                IcebergReader reader = readerProvider.get(new IcebergFragmentMetadata(scan, task));
                try (reader) {
                    if (!reader.open()) {
                        continue;
                    }
                    for (OneRow row = reader.next(); row != null; row = reader.next()) {
                        rows.add(resolver.getFields(row));
                    }
                }
            }
        }

        assertEquals(ROWS, rows.size());
        for (int i = 0; i < ROWS; i++) {
            List<OneField> row = rows.get(i);
            assertEquals(i, row.get(0).val);
            assertBytes(bin(i), row.get(1).val);
            assertBytes(fix(i), row.get(2).val);
        }
    }

    // PXF writes a bytea field to the database with Array.getLength()/Array.getByte(), so it has to be a byte[]
    private static void assertBytes(byte[] expected, Object actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertInstanceOf(byte[].class, actual);
        assertEquals(HexFormat.of().formatHex(expected), HexFormat.of().formatHex((byte[]) actual));
    }

    private static List<OneField> fields(int i) {
        return Arrays.asList(
                new OneField(DataType.INTEGER.getOID(), i),
                new OneField(DataType.BYTEA.getOID(), bin(i)),
                new OneField(DataType.BYTEA.getOID(), fix(i))
        );
    }

    private static byte[] bin(int i) {
        if (i == ROWS - 1) {
            return null;
        }
        // includes zero bytes and bytes above 0x7f
        byte[] value = new byte[i * 3];
        for (int j = 0; j < value.length; j++) {
            value[j] = (byte) (j * 97 + i);
        }
        return value;
    }

    private static byte[] fix(int i) {
        return i == ROWS - 1 ? null : new byte[]{(byte) i, 0, (byte) 0xff, (byte) 0x80};
    }

    private static void setBytes(BytesColumnVector vector, int row, byte[] value) {
        if (value == null) {
            vector.noNulls = false;
            vector.isNull[row] = true;
        } else {
            vector.setVal(row, value);
        }
    }
}
