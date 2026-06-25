package io.arenadata.pxf.plugins.iceberg.reader;

import org.apache.iceberg.FileFormat;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Component
public class ReaderProvider {

    private final Map<FileFormat, ReaderFactory> factories;

    public ReaderProvider(Collection<ReaderFactory> factories) {
        this.factories = factories.stream().collect(toMap(ReaderFactory::getFileFormat, Function.identity()));
    }

    public IcebergReader get(IcebergFragmentMetadata fragmentMetadata){
        return Optional.ofNullable(factories.get(fragmentMetadata.getFileFormat()))
                .map(factory -> factory.create(fragmentMetadata))
                .orElseThrow(() -> new UnsupportedOperationException("File format " + fragmentMetadata.getFileFormat() + " is not supported"));
    }


}
