package org.apache.cloudberry.pxf.plugins.iceberg.converters;

import org.apache.cloudberry.pxf.api.error.PxfRuntimeException;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IcebergConvertersTest {

    private final IcebergConverter<?> float8ToFloat = IcebergConverters.getIcebergConverter(DataType.FLOAT8, Types.FloatType.get());

    @Test
    public void testFloat8ToFloat() {
        assertEquals(1.5f, float8ToFloat.convertFromCloudberryToIceberg("1.5"));
        assertEquals(Float.POSITIVE_INFINITY, float8ToFloat.convertFromCloudberryToIceberg("Infinity"));
        assertEquals(Float.NEGATIVE_INFINITY, float8ToFloat.convertFromCloudberryToIceberg("-Infinity"));
        assertTrue(((Float) float8ToFloat.convertFromCloudberryToIceberg("NaN")).isNaN());
        assertEquals(0.0f, float8ToFloat.convertFromCloudberryToIceberg("0"));
    }

    @Test
    public void testFloat8ToFloatOutOfRange() {
        // a cast to real fails for these values, they must not become Infinity or 0
        assertThrows(PxfRuntimeException.class, () -> float8ToFloat.convertFromCloudberryToIceberg("1e+300"));
        assertThrows(PxfRuntimeException.class, () -> float8ToFloat.convertFromCloudberryToIceberg("-1e+300"));
        assertThrows(PxfRuntimeException.class, () -> float8ToFloat.convertFromCloudberryToIceberg("1e-300"));
    }

    @Test
    public void testNarrowerIntegerColumns() {
        assertEquals(5L, IcebergConverters.getIcebergConverter(DataType.INTEGER, Types.LongType.get()).convertFromCloudberryToIceberg("5"));
        assertEquals(5, IcebergConverters.getIcebergConverter(DataType.SMALLINT, Types.IntegerType.get()).convertFromCloudberryToIceberg("5"));

        IcebergConverter<?> longToInteger = IcebergConverters.getIcebergConverter(DataType.INTEGER, Types.LongType.get());
        assertEquals(5, longToInteger.convertFromIcebergToCloudberry(5L));
        assertThrows(PxfRuntimeException.class, () -> longToInteger.convertFromIcebergToCloudberry(1L << 40));
        assertEquals((short) 5, IcebergConverters.getIcebergConverter(DataType.SMALLINT, Types.IntegerType.get()).convertFromIcebergToCloudberry(5));
    }
}
