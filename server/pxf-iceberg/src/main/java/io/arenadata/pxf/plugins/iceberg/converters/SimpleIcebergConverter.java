package io.arenadata.pxf.plugins.iceberg.converters;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.iceberg.types.Type;
import org.greenplum.pxf.api.io.DataType;

import java.util.function.Function;

@RequiredArgsConstructor
@Getter
public class SimpleIcebergConverter<V> implements IcebergConverter<V> {

    private final Type icebergType;
    private final DataType greengageType;
    private final Function<String, V> icebergConverterFunction;
    private final Function<Object, Object> greengageConverterFunction;

    public SimpleIcebergConverter(Type icebergType, DataType greengageType, Function<String, V> icebergConverterFunction) {
        this(icebergType, greengageType, icebergConverterFunction, Function.identity());
    }

    @Override
    public V convertFromGreengageToIceberg(Object input) {
        return input != null ? icebergConverterFunction.apply(input.toString()) : null;
    }

    @Override
    public Object convertFromIcebergToGreengage(Object input) {
        return input != null ? greengageConverterFunction.apply(input) : null;
    }
}
