package org.apache.iceberg.orc;

import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.util.Pair;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;

import java.io.IOException;

class PxfVectorizedRowBatchIterator implements CloseableIterator<Pair<VectorizedRowBatch, Long>> {

    private final String fileLocation;
    private final RecordReader rows;
    private final VectorizedRowBatch batch;
    private boolean advanced = false;
    private long batchOffsetInFile = 0;

    PxfVectorizedRowBatchIterator(
            String fileLocation, TypeDescription schema, RecordReader rows, int recordsPerBatch) {
        this.fileLocation = fileLocation;
        this.rows = rows;
        this.batch = schema.createRowBatch(recordsPerBatch);
    }

    @Override
    public void close() throws IOException {
        rows.close();
    }

    private void advance() {
        if (!advanced) {
            try {
                batchOffsetInFile = rows.getRowNumber();
                rows.nextBatch(batch);
            } catch (IOException ioe) {
                throw new RuntimeIOException(ioe, "Problem reading ORC file %s", fileLocation);
            }
            advanced = true;
        }
    }

    @Override
    public boolean hasNext() {
        advance();
        return batch.size > 0;
    }

    @Override
    public Pair<VectorizedRowBatch, Long> next() {
        advance();
        advanced = false;
        return Pair.of(batch, batchOffsetInFile);
    }
}
