package org.apache.iceberg.orc;

import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;

public interface PxfOrcRowReader<T> {

    T read(VectorizedRowBatch batch, int row);

    void setBatchContext(long batchOffsetInFile);
}
