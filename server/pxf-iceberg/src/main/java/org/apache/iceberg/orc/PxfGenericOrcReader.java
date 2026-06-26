package org.apache.iceberg.orc;

import org.apache.hadoop.hive.ql.exec.vector.StructColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PxfGenericOrcReader implements PxfOrcRowReader<Record> {

    private final PxfOrcValueReader<?> reader;

    public PxfGenericOrcReader(
            Schema expectedSchema, TypeDescription readOrcSchema, Map<Integer, ?> idToConstant) {
        this.reader =
                OrcSchemaWithTypeVisitor.visit(
                        expectedSchema, readOrcSchema, new ReadBuilder(idToConstant));
    }

    public static PxfOrcRowReader<Record> buildReader(
            Schema expectedSchema, TypeDescription fileSchema) {
        return new PxfGenericOrcReader(expectedSchema, fileSchema, Collections.emptyMap());
    }

    public static PxfOrcRowReader<Record> buildReader(
            Schema expectedSchema, TypeDescription fileSchema, Map<Integer, ?> idToConstant) {
        return new PxfGenericOrcReader(expectedSchema, fileSchema, idToConstant);
    }

    @Override
    public Record read(VectorizedRowBatch batch, int row) {
        return (Record) reader.read(new StructColumnVector(batch.size, batch.cols), row);
    }

    @Override
    public void setBatchContext(long batchOffsetInFile) {
        reader.setBatchContext(batchOffsetInFile);
    }

    private static class ReadBuilder extends OrcSchemaWithTypeVisitor<PxfOrcValueReader<?>> {
        private final Map<Integer, ?> idToConstant;

        private ReadBuilder(Map<Integer, ?> idToConstant) {
            this.idToConstant = idToConstant;
        }

        @Override
        public PxfOrcValueReader<?> record(
                Types.StructType expected,
                TypeDescription record,
                List<String> names,
                List<PxfOrcValueReader<?>> fields) {
            return PxfGenericOrcReaders.struct(fields, expected, idToConstant);
        }

        @Override
        public PxfOrcValueReader<?> list(
                Types.ListType iList, TypeDescription array, PxfOrcValueReader<?> elementReader) {
            return PxfGenericOrcReaders.array(elementReader);
        }

        @Override
        public PxfOrcValueReader<?> map(
                Types.MapType iMap,
                TypeDescription map,
                PxfOrcValueReader<?> keyReader,
                PxfOrcValueReader<?> valueReader) {
            return PxfGenericOrcReaders.map(keyReader, valueReader);
        }

        @Override
        public PxfOrcValueReader<?> primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
            if (iPrimitive == null) {
                return null;
            }

            switch (primitive.getCategory()) {
                case BOOLEAN:
                    return PxfOrcValueReaders.booleans();
                case BYTE:
                case SHORT:
                case INT:
                    return PxfOrcValueReaders.ints();
                case LONG:
                    switch (iPrimitive.typeId()) {
                        case TIME:
                            return PxfGenericOrcReaders.times();
                        case LONG:
                            return PxfOrcValueReaders.longs();
                        default:
                            throw new IllegalStateException(
                                    String.format(
                                            "Invalid iceberg type %s corresponding to ORC type %s",
                                            iPrimitive, primitive));
                    }
                case FLOAT:
                    return PxfOrcValueReaders.floats();
                case DOUBLE:
                    return PxfOrcValueReaders.doubles();
                case DATE:
                    return PxfGenericOrcReaders.dates();
                case TIMESTAMP:
                    return PxfGenericOrcReaders.timestamps();
                case TIMESTAMP_INSTANT:
                    return PxfGenericOrcReaders.timestampTzs();
                case DECIMAL:
                    return PxfGenericOrcReaders.decimals();
                case CHAR:
                case VARCHAR:
                case STRING:
                    return PxfGenericOrcReaders.strings();
                case BINARY:
                    switch (iPrimitive.typeId()) {
                        case UUID:
                            return PxfGenericOrcReaders.uuids();
                        case FIXED:
                            return PxfOrcValueReaders.bytes();
                        case BINARY:
                            return PxfGenericOrcReaders.bytes();
                        default:
                            throw new IllegalStateException(
                                    String.format(
                                            "Invalid iceberg type %s corresponding to ORC type %s",
                                            iPrimitive, primitive));
                    }
                default:
                    throw new IllegalArgumentException("Unhandled type " + primitive);
            }
        }
    }
}
