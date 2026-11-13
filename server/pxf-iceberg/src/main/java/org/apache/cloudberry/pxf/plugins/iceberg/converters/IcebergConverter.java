package org.apache.cloudberry.pxf.plugins.iceberg.converters;

import org.apache.iceberg.types.Type;
import org.apache.cloudberry.pxf.api.io.DataType;

public interface IcebergConverter<V> {

    Type getIcebergType();

    DataType getCloudberryType();

    V convertFromCloudberryToIceberg(Object input);

    Object convertFromIcebergToCloudberry(Object input);
}
