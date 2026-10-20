package org.apache.cloudberry.pxf.plugins.iceberg.converters;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.iceberg.types.Type;
import org.apache.cloudberry.pxf.api.io.DataType;

import java.util.function.Function;

@RequiredArgsConstructor
@Getter
public class SimpleIcebergConverter<V> implements IcebergConverter<V> {

    private final Type icebergType;
    private final DataType cloudberryType;
    private final Function<String, V> icebergConverterFunction;
    private final Function<Object, Object> cloudberryConverterFunction;

    public SimpleIcebergConverter(Type icebergType, DataType cloudberryType, Function<String, V> icebergConverterFunction) {
        this(icebergType, cloudberryType, icebergConverterFunction, Function.identity());
    }

    @Override
    public V convertFromCloudberryToIceberg(Object input) {
        return input != null ? icebergConverterFunction.apply(input.toString()) : null;
    }

    @Override
    public Object convertFromIcebergToCloudberry(Object input) {
        return input != null ? cloudberryConverterFunction.apply(input) : null;
    }
}
