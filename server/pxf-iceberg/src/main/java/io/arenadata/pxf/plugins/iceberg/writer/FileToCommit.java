package io.arenadata.pxf.plugins.iceberg.writer;

import org.apache.iceberg.*;

import java.util.stream.IntStream;

public record FileToCommit(
        String path,
        long fileSizeInBytes,
        long recordCount,
        Object[] partitionData
){
    public static FileToCommit from(DataFile dataFile) {
        return new FileToCommit(
                dataFile.location(),
                dataFile.fileSizeInBytes(),
                dataFile.recordCount(),
                transformPartition(dataFile.partition())
        );
    }

    private static Object[] transformPartition(StructLike partition) {
        if(partition instanceof PartitionData pd) {
            if(pd.size() == 0) {
                return null;
            }
            return IntStream.range(0, partition.size()).mapToObj(pd::get).toArray();
        }
        return null;
    }

    public DataFile toDataFile(PartitionSpec partitionSpec) {
        return DataFiles.builder(partitionSpec)
                .withFormat(FileFormat.PARQUET)
                .withPath(path)
                .withFileSizeInBytes(fileSizeInBytes)
                .withRecordCount(recordCount)
                .withPartition(createPartitionData(partitionSpec, partitionData))
                .build();
    }

    private PartitionData createPartitionData(PartitionSpec partitionSpec, Object[] partitionData) {
        if(partitionData == null) {
            return null;
        }
        var data = new PartitionData(partitionSpec.partitionType());
        IntStream.range(0, partitionData.length).forEach(i -> data.put(i, partitionData[i]));
        return data;
    }
}
