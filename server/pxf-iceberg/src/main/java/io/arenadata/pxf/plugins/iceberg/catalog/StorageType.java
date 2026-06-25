package io.arenadata.pxf.plugins.iceberg.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public enum StorageType {
    HDFS,
    S3(Pattern.compile("s3://.*"), Pattern.compile("s3a://.*"), Pattern.compile("s3n://.*")),
    Ozone,
    ;

    private final List<Pattern> warehousePatterns;

    StorageType(Pattern... warehousePatterns) {
        this.warehousePatterns = Arrays.asList(warehousePatterns);
    }

    public boolean checkLocation(String warehouse) {
        return warehousePatterns.stream().anyMatch(pattern -> pattern.matcher(warehouse).matches());
    }

    public static Optional<StorageType> findByWarehouse(String warehouse) {
        if(warehouse == null) {
            return Optional.empty();
        }
        return Stream.of(values()).filter(st -> st.checkLocation(warehouse)).findAny();
    }
}
