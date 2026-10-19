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
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.utilities.ColumnDescriptor;
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
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DecimalColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.TimestampColumnVector;
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
import org.apache.iceberg.data.Record;
import org.apache.iceberg.expressions.Expressions;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Writes rows to a local Hadoop table and reads them back through the plugin's
 * readers, to make sure Iceberg works with the Parquet, ORC, Avro and Hadoop
 * versions bundled with PXF.
 */
public class IcebergRoundTripTest {

    private static final Schema SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.IntegerType.get()),
            Types.NestedField.optional(2, "name", Types.StringType.get()),
            Types.NestedField.optional(3, "amount", Types.DecimalType.of(10, 2)),
            Types.NestedField.optional(4, "created", Types.DateType.get()),
            Types.NestedField.optional(5, "updated", Types.TimestampType.withoutZone())
    );

    private static final int ROWS = 10;

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
        context.getTupleDescription().add(new ColumnDescriptor("name", DataType.TEXT.getOID(), 1, "text", null));
        context.getTupleDescription().add(new ColumnDescriptor("amount", DataType.NUMERIC.getOID(), 2, "numeric", null));
        context.getTupleDescription().add(new ColumnDescriptor("created", DataType.DATE.getOID(), 3, "date", null));
        context.getTupleDescription().add(new ColumnDescriptor("updated", DataType.TIMESTAMP.getOID(), 4, "timestamp", null));

        resolver = new IcebergResolver();
        resolver.setRequestContext(context);
    }

    @Test
    public void testWriteParquetAndReadBack() throws Exception {
        TableWrapper tableWrapper = new TableWrapper(Retry.ofDefaults("test"), table);
        IcebergWriter writer = new WriterFactory().create(tableWrapper, context, null);
        for (int i = 0; i < ROWS; i++) {
            writer.write((Record) resolver.setFields(fields(i)).getData());
        }
        tableWrapper.appendFiles(null, writer.completeAndGetFilesToCommit());

        assertRows(table.newScan(), 0);
    }

    @ParameterizedTest
    @EnumSource(value = FileFormat.class, names = {"PARQUET", "ORC", "AVRO"})
    public void testReadFileFormat(FileFormat format) throws Exception {
        appendDataFile(format);

        assertRows(table.newScan(), 0);
    }

    @ParameterizedTest
    @EnumSource(value = FileFormat.class, names = {"PARQUET", "ORC", "AVRO"})
    public void testReadWithFilter(FileFormat format) throws Exception {
        appendDataFile(format);

        assertRows(table.newScan().filter(Expressions.greaterThanOrEqual("id", 5)), 5);
    }

    private void appendDataFile(FileFormat format) throws Exception {
        File file = tempDir.resolve("data." + format.name().toLowerCase()).toFile();
        DataFiles.Builder builder = DataFiles.builder(PartitionSpec.unpartitioned())
                .withPath(file.getAbsolutePath())
                .withFormat(format);
        if (format == FileFormat.ORC) {
            // Iceberg's own ORC writer needs the "nohive" flavor of orc-core, while PXF ships the
            // regular one, so write a plain ORC file and let the reader map columns by name
            writeOrcFile(file);
            builder.withRecordCount(ROWS);
        } else {
            FileAppender<Record> appender = new GenericAppenderFactory(SCHEMA).newAppender(Files.localOutput(file), format);
            try (appender) {
                for (int i = 0; i < ROWS; i++) {
                    appender.add((Record) resolver.setFields(fields(i)).getData());
                }
            }
            builder.withMetrics(appender.metrics());
        }
        DataFile dataFile = builder.withFileSizeInBytes(file.length()).build();
        table.newAppend().appendFile(dataFile).commit();
    }

    private void writeOrcFile(File file) throws Exception {
        TypeDescription orcSchema = TypeDescription.fromString(
                "struct<id:int,name:string,amount:decimal(10,2),created:date,updated:timestamp>");
        Writer writer = OrcFile.createWriter(new org.apache.hadoop.fs.Path(file.getAbsolutePath()),
                OrcFile.writerOptions(new Configuration()).setSchema(orcSchema).useUTCTimestamp(true));
        try (writer) {
            VectorizedRowBatch batch = orcSchema.createRowBatch(ROWS);
            for (int i = 0; i < ROWS; i++) {
                ((LongColumnVector) batch.cols[0]).vector[i] = i;
                ((BytesColumnVector) batch.cols[1]).setVal(i, ("name-" + i).getBytes(StandardCharsets.UTF_8));
                ((DecimalColumnVector) batch.cols[2]).set(i, HiveDecimal.create(i + ".25"));
                ((LongColumnVector) batch.cols[3]).vector[i] = LocalDate.parse("2024-01-0" + (i % 9 + 1)).toEpochDay();
                ((TimestampColumnVector) batch.cols[4]).set(i,
                        Timestamp.from(LocalDateTime.parse("2024-01-02T03:04:0" + i).toInstant(ZoneOffset.UTC)));
            }
            batch.size = ROWS;
            writer.addRowBatch(batch);
        }
    }

    private void assertRows(TableScan scan, int firstId) throws Exception {
        ReaderProvider readerProvider = new ReaderProvider(
                List.of(new ParquetReaderFactory(), new OrcReaderFactory(), new AvroReaderFactory()));
        List<List<String>> actual = new ArrayList<>();
        try (CloseableIterable<FileScanTask> tasks = scan.planFiles()) {
            for (FileScanTask task : tasks) {
                IcebergReader reader = readerProvider.get(new IcebergFragmentMetadata(scan, task));
                try (reader) {
                    if (!reader.open()) {
                        continue;
                    }
                    for (OneRow row = reader.next(); row != null; row = reader.next()) {
                        actual.add(resolver.getFields(row).stream().map(f -> String.valueOf(f.val)).toList());
                    }
                }
            }
        }

        List<List<String>> expected = new ArrayList<>();
        for (int i = firstId; i < ROWS; i++) {
            expected.add(List.of(String.valueOf(i), "name-" + i, i + ".25",
                    "2024-01-0" + (i % 9 + 1), LocalDateTime.parse("2024-01-02T03:04:0" + i).toString()));
        }
        assertEquals(expected, actual);
    }

    private static List<OneField> fields(int i) {
        return List.of(
                new OneField(DataType.INTEGER.getOID(), i),
                new OneField(DataType.TEXT.getOID(), "name-" + i),
                new OneField(DataType.NUMERIC.getOID(), i + ".25"),
                new OneField(DataType.DATE.getOID(), "2024-01-0" + (i % 9 + 1)),
                new OneField(DataType.TIMESTAMP.getOID(), "2024-01-02 03:04:0" + i)
        );
    }
}
