package org.apache.iceberg.orc;

import org.apache.hadoop.hive.ql.exec.vector.ColumnVector;

public interface PxfOrcValueReader<T> {

    default T read(ColumnVector vector, int row) {
        int rowIndex = vector.isRepeating ? 0 : row;
        if (!vector.noNulls && vector.isNull[rowIndex]) {
            return null;
        } else {
            return nonNullRead(vector, rowIndex);
        }
    }

    T nonNullRead(ColumnVector vector, int row);

    default void setBatchContext(long batchOffsetInFile) {}
}
