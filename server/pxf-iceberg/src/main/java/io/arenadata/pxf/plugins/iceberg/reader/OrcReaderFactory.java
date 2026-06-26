package io.arenadata.pxf.plugins.iceberg.reader;

import com.google.common.collect.Sets;
import org.apache.iceberg.orc.PxfGenericOrcReader;
import org.apache.iceberg.orc.PxfOrcReadBuilder;
import org.apache.iceberg.*;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.types.TypeUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrcReaderFactory implements ReaderFactory {

    @Override
    public FileFormat getFileFormat() {
        return FileFormat.ORC;
    }

    @Override
    public IcebergReader create(IcebergFragmentMetadata fragmentMetadata) {
        return new IcebergOrcReader(fragmentMetadata.tableScan(), fragmentMetadata.task());
    }

    public class IcebergOrcReader extends IcebergReader {

        public IcebergOrcReader(TableScan tableScan, FileScanTask task) {
            super(tableScan, task);
        }

        @Override
        protected CloseableIterable<Record> readFile(InputFile input, Schema fileProjection, Map<Integer, ?> partition) {
            Schema projectionWithoutConstantAndMetadataFields = TypeUtil.selectNot(
                    fileProjection, Sets.union(partition.keySet(), MetadataColumns.metadataFieldIds())
            );
            //ORC.ReadBuilder orc = ORC.read(input)
            // we have to use our custom reader implementation which is fully copied from iceberg one
            // to provide compatibility with orc classes in hive-exec library used by hive connector
            PxfOrcReadBuilder orc = new PxfOrcReadBuilder(input)
                    .project(projectionWithoutConstantAndMetadataFields)
                    .createReaderFunc(
                            fileSchema ->
                                    //GenericOrcReader.buildReader(fileProjection, fileSchema, partition))
                                    PxfGenericOrcReader.buildReader(fileProjection, fileSchema, partition))
                    .split(task.start(), task.length())
                    .caseSensitive(tableScan.isCaseSensitive())
                    .filter(task.residual());

            return orc.build();
        }

    }

}
