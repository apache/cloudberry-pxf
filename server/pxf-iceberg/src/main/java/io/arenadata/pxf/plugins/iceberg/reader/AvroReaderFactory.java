package io.arenadata.pxf.plugins.iceberg.reader;

import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.avro.PlannedDataReader;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AvroReaderFactory implements ReaderFactory {

    @Override
    public FileFormat getFileFormat() {
        return FileFormat.AVRO;
    }

    @Override
    public IcebergReader create(IcebergFragmentMetadata fragmentMetadata) {
        return new IcebergAvroReader(fragmentMetadata.tableScan(), fragmentMetadata.task());
    }

    public class IcebergAvroReader extends IcebergReader {

        public IcebergAvroReader(TableScan tableScan, FileScanTask task) {
            super(tableScan, task);
        }

        @Override
        protected CloseableIterable<Record> readFile(InputFile input, Schema fileProjection, Map<Integer, ?> partition) {
            Avro.ReadBuilder avro = Avro.read(input)
                    .project(fileProjection)
                    .createResolvingReader(schema -> PlannedDataReader.create(schema, partition))
                    .split(task.start(), task.length());
//                    if (reuseContainers) {
//                        avro.reuseContainers();
//                    }
            return avro.build();
        }

    }

}
