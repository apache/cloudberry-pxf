package io.arenadata.pxf.plugins.iceberg.reader;

import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetReaders;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.parquet.Parquet;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ParquetReaderFactory implements ReaderFactory {

    @Override
    public FileFormat getFileFormat() {
        return FileFormat.PARQUET;
    }

    @Override
    public IcebergReader create(IcebergFragmentMetadata fragmentMetadata) {
        return new IcebergParquetReader(fragmentMetadata.tableScan(), fragmentMetadata.task());
    }

    public class IcebergParquetReader extends IcebergReader {

        public IcebergParquetReader(TableScan tableScan, FileScanTask task) {
            super(tableScan, task);
        }

        @Override
        protected CloseableIterable<Record> readFile(InputFile input, Schema fileProjection, Map<Integer, ?> partition) {
            Parquet.ReadBuilder parquet = Parquet.read(input)
                    .project(fileProjection)
                    .createReaderFunc(
                            fileSchema ->
                                    GenericParquetReaders.buildReader(fileProjection, fileSchema, partition))
                    .split(task.start(), task.length())
                    .caseSensitive(tableScan.isCaseSensitive())
                    .filter(task.residual());
//                    if (reuseContainers) {
//                        parquet.reuseContainers();
//                    }

            return parquet.build();
        }

    }

}
