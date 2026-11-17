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

import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.error.UnsupportedTypeException;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.utilities.ColumnDescriptor;
import org.apache.cloudberry.pxf.plugins.iceberg.filtering.ExpressionBuilder;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.AvroReaderFactory;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.IcebergFragmentMetadata;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.IcebergReader;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.OrcReaderFactory;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.ParquetReaderFactory;
import org.apache.cloudberry.pxf.plugins.iceberg.reader.ReaderProvider;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ListColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.MapColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.StructColumnVector;
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
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.types.Type;
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
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads Iceberg lists of primitive values into array columns; other lists, maps and structs are not
 * supported, but don't keep the other columns of a table from being read.
 */
public class IcebergNestedTypesTest {

    private static final Types.StructType INNER = Types.StructType.of(
            Types.NestedField.optional(10, "d", Types.DoubleType.get()));
    private static final Types.StructType STRUCT = Types.StructType.of(
            Types.NestedField.optional(11, "a", Types.IntegerType.get()),
            Types.NestedField.optional(12, "b", Types.StringType.get()),
            Types.NestedField.optional(13, "c", INNER));

    private static final Schema SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.IntegerType.get()),
            Types.NestedField.optional(2, "ints", Types.ListType.ofOptional(20, Types.IntegerType.get())),
            Types.NestedField.optional(3, "strs", Types.ListType.ofOptional(21, Types.StringType.get())),
            Types.NestedField.optional(4, "bins", Types.ListType.ofOptional(22, Types.BinaryType.get())),
            Types.NestedField.optional(5, "st", STRUCT),
            Types.NestedField.optional(6, "mp", Types.MapType.ofOptional(23, 24, Types.StringType.get(), Types.IntegerType.get()))
    );

    // the values of the first row; the second row has only NULLs
    private static final List<Integer> INTS = Arrays.asList(2, 3, null, 4);
    private static final List<String> STRS = Arrays.asList("s2", "", "x,y", "q\"uote", null, "NULL", "back\\slash", "{}");
    private static final List<byte[]> BINS = Arrays.asList(new byte[]{0, (byte) 0xff}, new byte[0]);

    private static final List<String> EXPECTED_ROW = List.of(
            "0",
            "{\"2\",\"3\",NULL,\"4\"}",
            "{\"s2\",\"\",\"x,y\",\"q\\\"uote\",NULL,\"NULL\",\"back\\\\slash\",\"{}\"}",
            "{\"\\\\x00ff\",\"\\\\x\"}");

    @TempDir
    Path tempDir;

    private Table table;
    private IcebergResolver resolver;

    @BeforeEach
    public void setup() {
        table = new HadoopTables(new Configuration())
                .create(SCHEMA, PartitionSpec.unpartitioned(), Map.of(), tempDir.resolve("table").toString());

        RequestContext context = new RequestContext();
        context.setMetadata(table.schema());
        addColumn(context, "id", DataType.INTEGER, "int4");
        addColumn(context, "ints", DataType.INT4ARRAY, "int4[]");
        addColumn(context, "strs", DataType.TEXTARRAY, "text[]");
        addColumn(context, "bins", DataType.BYTEAARRAY, "bytea[]");
        // the struct and map columns of the table are not read

        resolver = new IcebergResolver();
        resolver.setRequestContext(context);
    }

    @ParameterizedTest
    @EnumSource(value = FileFormat.class, names = {"PARQUET", "AVRO"})
    public void testReadFileFormat(FileFormat format) throws Exception {
        File file = tempDir.resolve("data." + format.name().toLowerCase()).toFile();
        // the table has its own field ids, which the data files have to use
        Schema schema = table.schema();
        Types.StructType structType = schema.findType("st").asStructType();
        FileAppender<Record> appender = new GenericAppenderFactory(schema).newAppender(Files.localOutput(file), format);
        try (appender) {
            Record record = GenericRecord.create(schema);
            record.setField("id", 0);
            record.setField("ints", INTS);
            record.setField("strs", STRS);
            record.setField("bins", BINS.stream().map(ByteBuffer::wrap).toList());
            Record inner = GenericRecord.create(structType.fieldType("c").asStructType());
            inner.setField("d", Double.NaN);
            Record struct = GenericRecord.create(structType);
            struct.setField("a", 1);
            struct.setField("b", "b1");
            struct.setField("c", inner);
            record.setField("st", struct);
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("k1", 1);
            map.put("z", null);
            record.setField("mp", map);
            appender.add(record);

            Record nulls = GenericRecord.create(schema);
            nulls.setField("id", 1);
            appender.add(nulls);
        }
        appendFile(file, format, 2);

        assertRows(table.newScan());
    }

    @Test
    public void testReadOrc() throws Exception {
        File file = tempDir.resolve("data.orc").toFile();
        // Iceberg's own ORC writer needs the "nohive" flavor of orc-core, so write a plain ORC file
        TypeDescription orcSchema = TypeDescription.fromString("struct<id:int,ints:array<int>,strs:array<string>,"
                + "bins:array<binary>,st:struct<a:int,b:string,c:struct<d:double>>,mp:map<string,int>>");
        Writer writer = OrcFile.createWriter(new org.apache.hadoop.fs.Path(file.getAbsolutePath()),
                OrcFile.writerOptions(new Configuration()).setSchema(orcSchema));
        try (writer) {
            VectorizedRowBatch batch = orcSchema.createRowBatch(2);
            ((LongColumnVector) batch.cols[0]).vector[0] = 0;
            ((LongColumnVector) batch.cols[0]).vector[1] = 1;
            setList((ListColumnVector) batch.cols[1], INTS,
                    (child, i, value) -> ((LongColumnVector) child).vector[i] = (Integer) value);
            setList((ListColumnVector) batch.cols[2], STRS,
                    (child, i, value) -> ((BytesColumnVector) child).setVal(i, ((String) value).getBytes(StandardCharsets.UTF_8)));
            setList((ListColumnVector) batch.cols[3], BINS,
                    (child, i, value) -> ((BytesColumnVector) child).setVal(i, (byte[]) value));

            StructColumnVector struct = (StructColumnVector) batch.cols[4];
            ((LongColumnVector) struct.fields[0]).vector[0] = 1;
            ((BytesColumnVector) struct.fields[1]).setVal(0, "b1".getBytes(StandardCharsets.UTF_8));
            ((DoubleColumnVector) ((StructColumnVector) struct.fields[2]).fields[0]).vector[0] = Double.NaN;
            setNull(struct, 1);

            MapColumnVector map = (MapColumnVector) batch.cols[5];
            map.offsets[0] = 0;
            map.lengths[0] = 2;
            map.childCount = 2;
            ((BytesColumnVector) map.keys).setVal(0, "k1".getBytes(StandardCharsets.UTF_8));
            ((BytesColumnVector) map.keys).setVal(1, "z".getBytes(StandardCharsets.UTF_8));
            ((LongColumnVector) map.values).vector[0] = 1;
            setNull(map.values, 1);
            setNull(map, 1);
            batch.size = 2;
            writer.addRowBatch(batch);
        }
        appendFile(file, FileFormat.ORC, 2);

        assertRows(table.newScan());
    }

    @Test
    public void testReadListIntoArrayOfOtherElementType() throws Exception {
        // the elements go through the converter of the array element type, as for a scalar column
        assertEquals("{\"1\",\"2\"}", readValue(Types.ListType.ofOptional(20, Types.LongType.get()),
                DataType.INT4ARRAY, List.of(1L, 2L)));
    }

    @Test
    public void testReadArrayElementTypes() throws Exception {
        // the texts are the external representation that Postgres parses into the same values
        assertEquals("{\"true\",\"false\"}",
                readValue(Types.ListType.ofOptional(20, Types.BooleanType.get()), DataType.BOOLARRAY, List.of(true, false)));
        assertEquals("{\"-9000000000\"}",
                readValue(Types.ListType.ofOptional(20, Types.LongType.get()), DataType.INT8ARRAY, List.of(-9000000000L)));
        assertEquals("{\"1.5\",\"1.0E10\",\"NaN\",\"-Infinity\",\"-0.0\"}",
                readValue(Types.ListType.ofOptional(20, Types.FloatType.get()), DataType.FLOAT4ARRAY, List.of(1.5f, 1e10f, Float.NaN, Float.NEGATIVE_INFINITY, -0.0f)));
        assertEquals("{\"1.0E300\",\"1.0E-300\",\"Infinity\"}",
                readValue(Types.ListType.ofOptional(20, Types.DoubleType.get()), DataType.FLOAT8ARRAY, List.of(1e300, 1e-300, Double.POSITIVE_INFINITY)));
        assertEquals("{\"1E+3\",\"-12345678901234567890.0123456789\"}",
                readValue(Types.ListType.ofOptional(20, Types.DecimalType.of(38, 10)), DataType.NUMERICARRAY, List.of(new BigDecimal("1E+3"), new BigDecimal("-12345678901234567890.0123456789"))));
        assertEquals("{\"2024-02-29\",\"0001-01-01\"}",
                readValue(Types.ListType.ofOptional(20, Types.DateType.get()), DataType.DATEARRAY, List.of(LocalDate.of(2024, 2, 29), LocalDate.of(1, 1, 1))));
        assertEquals("{\"13:14\",\"01:02:03.000456\"}",
                readValue(Types.ListType.ofOptional(20, Types.TimeType.get()), DataType.TIMEARRAY, List.of(LocalTime.of(13, 14), LocalTime.of(1, 2, 3, 456000))));
        assertEquals("{\"2024-01-02 03:04:00\",\"2024-01-02 03:04:05.123456\"}",
                readValue(Types.ListType.ofOptional(20, Types.TimestampType.withoutZone()), DataType.TIMESTAMPARRAY, List.of(LocalDateTime.of(2024, 1, 2, 3, 4), LocalDateTime.of(2024, 1, 2, 3, 4, 5, 123456000))));
        assertEquals("{\"2024-01-02 03:04:00Z\"}",
                readValue(Types.ListType.ofOptional(20, Types.TimestampType.withZone()), DataType.TIMESTAMP_WITH_TIMEZONE_ARRAY, List.of(OffsetDateTime.of(2024, 1, 2, 3, 4, 0, 0, ZoneOffset.UTC))));
        assertEquals("{\"a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11\"}",
                readValue(Types.ListType.ofOptional(20, Types.UUIDType.get()), DataType.UUIDARRAY, List.of(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"))));
        assertEquals("{\"a b\",\"é\"}",
                readValue(Types.ListType.ofOptional(20, Types.StringType.get()), DataType.VARCHARARRAY, List.of("a b", "é")));
    }

    @Test
    public void testReadNestedIntoText() {
        for (Type type : List.of(STRUCT, Types.MapType.ofOptional(23, 24, Types.StringType.get(), Types.IntegerType.get()),
                Types.ListType.ofOptional(20, Types.IntegerType.get()))) {
            UnsupportedTypeException e = assertThrows(UnsupportedTypeException.class,
                    () -> readValue(type, DataType.TEXT, null));
            assertTrue(e.getMessage().contains("Column c of type TEXT: Iceberg column of type " + type
                    + " cannot be read into a TEXT column"), e.getMessage());
        }
    }

    @Test
    public void testUnselectedNestedColumnIsNotRead() {
        // SELECT id FROM t, where t defines a text column over an Iceberg struct: the scan doesn't read the column
        Schema schema = new Schema(
                Types.NestedField.required(1, "id", Types.IntegerType.get()),
                Types.NestedField.optional(2, "st", STRUCT));
        RequestContext context = new RequestContext();
        context.setMetadata(schema);
        context.getTupleDescription().add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null, true));
        context.getTupleDescription().add(new ColumnDescriptor("st", DataType.TEXT.getOID(), 1, "text", null, false));
        IcebergResolver resolver = new IcebergResolver();
        resolver.setRequestContext(context);
        Record record = GenericRecord.create(schema.select("id"));
        record.setField("id", 7);

        List<OneField> fields = resolver.getFields(new OneRow(record));
        assertEquals(7, fields.get(0).val);
        assertNull(fields.get(1).val);
    }

    @Test
    public void testReadStringIntoArray() throws Exception {
        // an array column over a string column takes its value as the text of the array, as before
        assertEquals("{a,b}", readValue(Types.StringType.get(), DataType.TEXTARRAY, "{a,b}"));
    }

    @Test
    public void testReadListOfListsIntoArray() {
        UnsupportedTypeException e = assertThrows(UnsupportedTypeException.class, () -> readValue(
                Types.ListType.ofOptional(20, Types.ListType.ofOptional(21, Types.IntegerType.get())), DataType.INT4ARRAY, null));
        assertTrue(e.getMessage().contains("only lists of primitive values can be read, into array columns"), e.getMessage());
    }

    @Test
    public void testReadNonListIntoArray() {
        UnsupportedTypeException e = assertThrows(UnsupportedTypeException.class,
                () -> readValue(STRUCT, DataType.TEXTARRAY, null));
        assertTrue(e.getMessage().contains("only lists of primitive values can be read, into array columns"), e.getMessage());
    }

    @Test
    public void testFiltersOnNestedColumnsAreNotPushedDown() {
        ExpressionBuilder builder = ExpressionBuilder.create(List.of(
                        new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null, true),
                        new ColumnDescriptor("st", DataType.TEXT.getOID(), 1, "text", null, true)),
                Map.of("id", Types.IntegerType.get(), "st", STRUCT));

        // st = 'x' AND id > 1
        Expression expression = builder.build("a1c25s1dxo5a0c23s1d1o2l0");
        assertEquals(Expressions.greaterThan("id", 1).toString(), expression.toString());
        // Iceberg fails to bind a comparison of a struct column
        Binder.bind(new Schema(Types.NestedField.optional(1, "id", Types.IntegerType.get()),
                Types.NestedField.optional(2, "st", STRUCT)).asStruct(), expression, true);
    }

    private Object readValue(Type icebergType, DataType dataType, Object value) throws Exception {
        Schema schema = new Schema(Types.NestedField.optional(1, "c", icebergType));
        RequestContext context = new RequestContext();
        context.setMetadata(schema);
        addColumn(context, "c", dataType, dataType.name());
        IcebergResolver resolver = new IcebergResolver();
        resolver.setRequestContext(context);
        Record record = GenericRecord.create(schema);
        record.setField("c", value);
        return resolver.getFields(new OneRow(record)).get(0).val;
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
        List<List<String>> rows = new ArrayList<>();
        try (CloseableIterable<FileScanTask> tasks = scan.planFiles()) {
            for (FileScanTask task : tasks) {
                IcebergReader reader = readerProvider.get(new IcebergFragmentMetadata(scan, task));
                try (reader) {
                    if (!reader.open()) {
                        continue;
                    }
                    for (OneRow row = reader.next(); row != null; row = reader.next()) {
                        rows.add(resolver.getFields(row).stream().map(f -> String.valueOf(f.val)).toList());
                    }
                }
            }
        }

        assertEquals(List.of(EXPECTED_ROW, List.of("1", "null", "null", "null")), rows);
    }

    private static void addColumn(RequestContext context, String name, DataType type, String typeName) {
        context.getTupleDescription().add(new ColumnDescriptor(name, type.getOID(),
                context.getTupleDescription().size(), typeName, null));
    }

    private interface ElementSetter {
        void set(ColumnVector child, int index, Object value);
    }

    /**
     * Sets the list of the first row, and NULL for the second row.
     */
    private static void setList(ListColumnVector list, List<?> values, ElementSetter setter) {
        list.offsets[0] = 0;
        list.lengths[0] = values.size();
        list.childCount = values.size();
        list.child.ensureSize(values.size(), false);
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) {
                setNull(list.child, i);
            } else {
                setter.set(list.child, i, values.get(i));
            }
        }
        setNull(list, 1);
    }

    private static void setNull(ColumnVector vector, int row) {
        vector.noNulls = false;
        vector.isNull[row] = true;
    }
}
