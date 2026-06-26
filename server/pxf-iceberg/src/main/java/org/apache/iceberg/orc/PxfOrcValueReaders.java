package org.apache.iceberg.orc;

import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.StructColumnVector;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PxfOrcValueReaders {

    private PxfOrcValueReaders() {}

    public static PxfOrcValueReader<Boolean> booleans() {
        return BooleanReader.INSTANCE;
    }

    public static PxfOrcValueReader<Integer> ints() {
        return IntegerReader.INSTANCE;
    }

    public static PxfOrcValueReader<Long> longs() {
        return LongReader.INSTANCE;
    }

    public static PxfOrcValueReader<Float> floats() {
        return FloatReader.INSTANCE;
    }

    public static PxfOrcValueReader<Double> doubles() {
        return DoubleReader.INSTANCE;
    }

    public static PxfOrcValueReader<byte[]> bytes() {
        return BytesReader.INSTANCE;
    }

    public static <C> PxfOrcValueReader<C> constants(C constant) {
        return new ConstantReader<>(constant);
    }

    private static class BooleanReader implements PxfOrcValueReader<Boolean> {
        static final BooleanReader INSTANCE = new BooleanReader();

        private BooleanReader() {}

        @Override
        public Boolean nonNullRead(ColumnVector vector, int row) {
            return ((LongColumnVector) vector).vector[row] != 0;
        }
    }

    private static class IntegerReader implements PxfOrcValueReader<Integer> {
        static final IntegerReader INSTANCE = new IntegerReader();

        private IntegerReader() {}

        @Override
        public Integer nonNullRead(ColumnVector vector, int row) {
            return (int) ((LongColumnVector) vector).vector[row];
        }
    }

    private static class LongReader implements PxfOrcValueReader<Long> {
        static final LongReader INSTANCE = new LongReader();

        private LongReader() {}

        @Override
        public Long nonNullRead(ColumnVector vector, int row) {
            return ((LongColumnVector) vector).vector[row];
        }
    }

    private static class FloatReader implements PxfOrcValueReader<Float> {
        private static final FloatReader INSTANCE = new FloatReader();

        private FloatReader() {}

        @Override
        public Float nonNullRead(ColumnVector vector, int row) {
            return (float) ((DoubleColumnVector) vector).vector[row];
        }
    }

    private static class DoubleReader implements PxfOrcValueReader<Double> {
        private static final DoubleReader INSTANCE = new DoubleReader();

        private DoubleReader() {}

        @Override
        public Double nonNullRead(ColumnVector vector, int row) {
            return ((DoubleColumnVector) vector).vector[row];
        }
    }

    private static class BytesReader implements PxfOrcValueReader<byte[]> {
        private static final BytesReader INSTANCE = new BytesReader();

        private BytesReader() {}

        @Override
        public byte[] nonNullRead(ColumnVector vector, int row) {
            BytesColumnVector bytesVector = (BytesColumnVector) vector;
            return Arrays.copyOfRange(
                    bytesVector.vector[row],
                    bytesVector.start[row],
                    bytesVector.start[row] + bytesVector.length[row]);
        }
    }

    public abstract static class StructReader<T> implements PxfOrcValueReader<T> {
        private final PxfOrcValueReader<?>[] readers;
        private final boolean[] isConstantOrMetadataField;

        protected StructReader(
                List<PxfOrcValueReader<?>> readers,
                Types.StructType struct,
                Map<Integer, ?> idToConstant) {
            List<Types.NestedField> fields = struct.fields();
            this.readers = new PxfOrcValueReader[fields.size()];
            this.isConstantOrMetadataField = new boolean[fields.size()];
            for (int pos = 0, readerIndex = 0; pos < fields.size(); pos += 1) {
                Types.NestedField field = fields.get(pos);
                if (idToConstant.containsKey(field.fieldId())) {
                    this.isConstantOrMetadataField[pos] = true;
                    this.readers[pos] = constants(idToConstant.get(field.fieldId()));
                } else if (field.equals(MetadataColumns.ROW_POSITION)) {
                    this.isConstantOrMetadataField[pos] = true;
                    this.readers[pos] = new RowPositionReader();
                } else if (field.equals(MetadataColumns.IS_DELETED)) {
                    this.isConstantOrMetadataField[pos] = true;
                    this.readers[pos] = constants(false);
                } else if (MetadataColumns.isMetadataColumn(field.name())
                        || field.type().typeId() == Type.TypeID.UNKNOWN) {
                    this.isConstantOrMetadataField[pos] = true;
                    this.readers[pos] = constants(null);
                } else {
                    this.readers[pos] = readers.get(readerIndex++);
                }
            }
        }

        protected abstract T create();

        protected abstract void set(T struct, int pos, Object value);

        public PxfOrcValueReader<?> reader(int pos) {
            return readers[pos];
        }

        @Override
        public T nonNullRead(ColumnVector vector, int row) {
            StructColumnVector structVector = (StructColumnVector) vector;
            return readInternal(create(), structVector.fields, row);
        }

        private T readInternal(T struct, ColumnVector[] columnVectors, int row) {
            for (int c = 0, vectorIndex = 0; c < readers.length; ++c) {
                ColumnVector vector;
                if (isConstantOrMetadataField[c]) {
                    vector = null;
                } else {
                    vector = columnVectors[vectorIndex];
                    vectorIndex++;
                }
                set(struct, c, reader(c).read(vector, row));
            }
            return struct;
        }

        @Override
        public void setBatchContext(long batchOffsetInFile) {
            for (PxfOrcValueReader<?> reader : readers) {
                reader.setBatchContext(batchOffsetInFile);
            }
        }
    }

    private static class ConstantReader<C> implements PxfOrcValueReader<C> {
        private final C constant;

        private ConstantReader(C constant) {
            this.constant = constant;
        }

        @Override
        public C read(ColumnVector ignored, int ignoredRow) {
            return constant;
        }

        @Override
        public C nonNullRead(ColumnVector ignored, int ignoredRow) {
            return constant;
        }
    }

    private static class RowPositionReader implements PxfOrcValueReader<Long> {
        private long batchOffsetInFile;

        @Override
        public Long read(ColumnVector ignored, int row) {
            return batchOffsetInFile + row;
        }

        @Override
        public Long nonNullRead(ColumnVector ignored, int row) {
            throw new UnsupportedOperationException("Use RowPositionReader.read()");
        }

        @Override
        public void setBatchContext(long newBatchOffsetInFile) {
            this.batchOffsetInFile = newBatchOffsetInFile;
        }
    }
}
