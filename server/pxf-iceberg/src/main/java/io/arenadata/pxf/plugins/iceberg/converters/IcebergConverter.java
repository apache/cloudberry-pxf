package io.arenadata.pxf.plugins.iceberg.converters;

import org.apache.iceberg.types.Type;
import org.greenplum.pxf.api.io.DataType;

public interface IcebergConverter<V> {

    Type getIcebergType();

    DataType getGreengageType();

    V convertFromGreengageToIceberg(Object input);

    Object convertFromIcebergToGreengage(Object input);
}
