package org.apache.iceberg.orc;

import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DecimalColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ListColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.MapColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.TimestampColumnVector;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.UUIDUtil;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;

public class PxfGenericOrcReaders {

    private PxfGenericOrcReaders() {}

    public static PxfOrcValueReader<Record> struct(
            List<PxfOrcValueReader<?>> readers,
            Types.StructType struct,
            Map<Integer, ?> idToConstant) {
        return new StructReader(readers, struct, idToConstant);
    }

    public static PxfOrcValueReader<List<?>> array(PxfOrcValueReader<?> elementReader) {
        return new ListReader(elementReader);
    }

    public static PxfOrcValueReader<Map<?, ?>> map(
            PxfOrcValueReader<?> keyReader, PxfOrcValueReader<?> valueReader) {
        return new MapReader(keyReader, valueReader);
    }

    public static PxfOrcValueReader<OffsetDateTime> timestampTzs() {
        return TimestampTzReader.INSTANCE;
    }

    public static PxfOrcValueReader<BigDecimal> decimals() {
        return DecimalReader.INSTANCE;
    }

    public static PxfOrcValueReader<String> strings() {
        return StringReader.INSTANCE;
    }

    public static PxfOrcValueReader<UUID> uuids() {
        return UUIDReader.INSTANCE;
    }

    public static PxfOrcValueReader<ByteBuffer> bytes() {
        return BytesReader.INSTANCE;
    }

    public static PxfOrcValueReader<LocalTime> times() {
        return TimeReader.INSTANCE;
    }

    public static PxfOrcValueReader<LocalDate> dates() {
        return DateReader.INSTANCE;
    }

    public static PxfOrcValueReader<LocalDateTime> timestamps() {
        return TimestampReader.INSTANCE;
    }

    private static class TimestampTzReader implements PxfOrcValueReader<OffsetDateTime> {
        public static final PxfOrcValueReader<OffsetDateTime> INSTANCE = new TimestampTzReader();

        private TimestampTzReader() {}

        @Override
        public OffsetDateTime nonNullRead(ColumnVector vector, int row) {
            TimestampColumnVector tcv = (TimestampColumnVector) vector;
            return Instant.ofEpochSecond(Math.floorDiv(tcv.time[row], 1_000), tcv.nanos[row])
                    .atOffset(ZoneOffset.UTC);
        }
    }

    private static class TimeReader implements PxfOrcValueReader<LocalTime> {
        public static final PxfOrcValueReader<LocalTime> INSTANCE = new TimeReader();

        private TimeReader() {}

        @Override
        public LocalTime nonNullRead(ColumnVector vector, int row) {
            return DateTimeUtil.timeFromMicros(((LongColumnVector) vector).vector[row]);
        }
    }

    private static class DateReader implements PxfOrcValueReader<LocalDate> {
        public static final PxfOrcValueReader<LocalDate> INSTANCE = new DateReader();

        private DateReader() {}

        @Override
        public LocalDate nonNullRead(ColumnVector vector, int row) {
            return DateTimeUtil.dateFromDays((int) ((LongColumnVector) vector).vector[row]);
        }
    }

    private static class TimestampReader implements PxfOrcValueReader<LocalDateTime> {
        public static final PxfOrcValueReader<LocalDateTime> INSTANCE = new TimestampReader();

        private TimestampReader() {}

        @Override
        public LocalDateTime nonNullRead(ColumnVector vector, int row) {
            TimestampColumnVector tcv = (TimestampColumnVector) vector;
            return Instant.ofEpochSecond(Math.floorDiv(tcv.time[row], 1_000), tcv.nanos[row])
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDateTime();
        }
    }

    private static class DecimalReader implements PxfOrcValueReader<BigDecimal> {
        public static final PxfOrcValueReader<BigDecimal> INSTANCE = new DecimalReader();

        private DecimalReader() {}

        @Override
        public BigDecimal nonNullRead(ColumnVector vector, int row) {
            DecimalColumnVector cv = (DecimalColumnVector) vector;
            return cv.vector[row].getHiveDecimal().bigDecimalValue().setScale(cv.scale);
        }
    }

    private static class StringReader implements PxfOrcValueReader<String> {
        public static final PxfOrcValueReader<String> INSTANCE = new StringReader();

        private StringReader() {}

        @Override
        public String nonNullRead(ColumnVector vector, int row) {
            BytesColumnVector bytesVector = (BytesColumnVector) vector;
            return new String(
                    bytesVector.vector[row],
                    bytesVector.start[row],
                    bytesVector.length[row],
                    StandardCharsets.UTF_8);
        }
    }

    private static class UUIDReader implements PxfOrcValueReader<UUID> {
        public static final PxfOrcValueReader<UUID> INSTANCE = new UUIDReader();

        private UUIDReader() {}

        @Override
        public UUID nonNullRead(ColumnVector vector, int row) {
            BytesColumnVector bytesVector = (BytesColumnVector) vector;
            ByteBuffer buf =
                    ByteBuffer.wrap(bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
            return UUIDUtil.convert(buf);
        }
    }

    private static class BytesReader implements PxfOrcValueReader<ByteBuffer> {
        public static final PxfOrcValueReader<ByteBuffer> INSTANCE = new BytesReader();

        private BytesReader() {}

        @Override
        public ByteBuffer nonNullRead(ColumnVector vector, int row) {
            BytesColumnVector bytesVector = (BytesColumnVector) vector;
            return ByteBuffer.wrap(
                    bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
        }
    }

    private static class VariantReader implements PxfOrcValueReader<Object> {
        private static final VariantReader INSTANCE = new VariantReader();

        @Override
        public Object nonNullRead(ColumnVector vector, int row) {
            throw new UnsupportedOperationException("Variant type is not supported in hive ORC reader");
        }
    }

    private static class StructReader extends PxfOrcValueReaders.StructReader<Record> {
        private final GenericRecord template;

        protected StructReader(
                List<PxfOrcValueReader<?>> readers,
                Types.StructType structType,
                Map<Integer, ?> idToConstant) {
            super(readers, structType, idToConstant);
            this.template = GenericRecord.create(structType);
        }

        @Override
        protected Record create() {
            return template.copy();
        }

        @Override
        protected void set(Record struct, int pos, Object value) {
            struct.set(pos, value);
        }
    }

    private static class MapReader implements PxfOrcValueReader<Map<?, ?>> {
        private final PxfOrcValueReader<?> keyReader;
        private final PxfOrcValueReader<?> valueReader;

        private MapReader(PxfOrcValueReader<?> keyReader, PxfOrcValueReader<?> valueReader) {
            this.keyReader = keyReader;
            this.valueReader = valueReader;
        }

        @Override
        public Map<?, ?> nonNullRead(ColumnVector vector, int row) {
            MapColumnVector mapVector = (MapColumnVector) vector;
            int offset = (int) mapVector.offsets[row];
            long length = mapVector.lengths[row];
            Map<Object, Object> map = Maps.newHashMapWithExpectedSize((int) length);
            for (int c = 0; c < length; c++) {
                map.put(
                        keyReader.read(mapVector.keys, offset + c),
                        valueReader.read(mapVector.values, offset + c));
            }
            return map;
        }

        @Override
        public void setBatchContext(long batchOffsetInFile) {
            keyReader.setBatchContext(batchOffsetInFile);
            valueReader.setBatchContext(batchOffsetInFile);
        }
    }

    private static class ListReader implements PxfOrcValueReader<List<?>> {
        private final PxfOrcValueReader<?> elementReader;

        private ListReader(PxfOrcValueReader<?> elementReader) {
            this.elementReader = elementReader;
        }

        @Override
        public List<?> nonNullRead(ColumnVector vector, int row) {
            ListColumnVector listVector = (ListColumnVector) vector;
            int offset = (int) listVector.offsets[row];
            int length = (int) listVector.lengths[row];
            List<Object> elements = Lists.newArrayListWithExpectedSize(length);
            for (int c = 0; c < length; ++c) {
                elements.add(elementReader.read(listVector.child, offset + c));
            }
            return elements;
        }

        @Override
        public void setBatchContext(long batchOffsetInFile) {
            elementReader.setBatchContext(batchOffsetInFile);
        }
    }
}
