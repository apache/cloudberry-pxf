package org.apache.cloudberry.pxf.plugins.iceberg.converters;

import org.apache.iceberg.types.Type;
import org.apache.cloudberry.pxf.api.io.DataType;

public interface IcebergConverter<V> {

    Type getIcebergType();

    DataType getGreengageType();

    V convertFromGreengageToIceberg(Object input);

    Object convertFromIcebergToGreengage(Object input);
}
