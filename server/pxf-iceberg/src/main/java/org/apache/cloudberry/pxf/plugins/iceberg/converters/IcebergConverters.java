package org.apache.cloudberry.pxf.plugins.iceberg.converters;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.cloudberry.pxf.api.GreenplumDateTime;
import org.apache.cloudberry.pxf.api.error.PxfRuntimeException;
import org.apache.cloudberry.pxf.api.io.DataType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public interface IcebergConverters {

    IcebergConverter<Object> unsupportedTypeConverter = new SimpleIcebergConverter<>(Types.UnknownType.get(), DataType.UNSUPPORTED_TYPE, Objects::toString);

    List<IcebergConverter<?>> converters = List.of(
            new SimpleIcebergConverter<>(Types.UUIDType.get(), DataType.UUID, UUID::fromString),
            // integer converter
            new SimpleIcebergConverter<>(Types.IntegerType.get(), DataType.INTEGER, Integer::parseInt),
            // long converter
            new SimpleIcebergConverter<>(Types.LongType.get(), DataType.BIGINT, Long::parseLong),
            // boolean converter
            new SimpleIcebergConverter<>(Types.BooleanType.get(), DataType.BOOLEAN, Boolean::parseBoolean),

            new SimpleIcebergConverter<>(Types.FloatType.get(), DataType.REAL, Float::valueOf),
            new SimpleIcebergConverter<>(Types.DoubleType.get(), DataType.FLOAT8, Double::parseDouble),
            new SimpleIcebergConverter<>(Types.FloatType.get(), DataType.FLOAT8, IcebergConverters::toFloat,
                    val -> Double.parseDouble(val.toString())
            ),
            new SimpleIcebergConverter<>(Types.DoubleType.get(), DataType.REAL, Double::parseDouble,
                    val -> toFloat(val.toString())
            ),
            // narrower integer columns, the way Cloudberry casts between them
            new SimpleIcebergConverter<>(Types.LongType.get(), DataType.INTEGER, Long::parseLong,
                    val -> toInteger((Long) val)
            ),
            new SimpleIcebergConverter<>(Types.IntegerType.get(), DataType.SMALLINT, Integer::parseInt,
                    val -> toShort(((Integer) val).longValue())
            ),
            new SimpleIcebergConverter<>(Types.LongType.get(), DataType.SMALLINT, Long::parseLong,
                    val -> toShort((Long) val)
            ),
            // decimal converter
            new SimpleIcebergConverter<>(Types.DecimalType.of(10, 5), DataType.FLOAT8, BigDecimal::new,
                    val -> ((BigDecimal) val).doubleValue()
            ),
            new SimpleIcebergConverter<>(Types.DecimalType.of(10, 5), DataType.NUMERIC, BigDecimal::new),
            //  date converter
            new SimpleIcebergConverter<>(Types.DateType.get(), DataType.DATE, LocalDate::parse),
            //  time converter
            new SimpleIcebergConverter<>(Types.TimeType.get(), DataType.TIME, LocalTime::parse),
            //  timestamp converter
            new SimpleIcebergConverter<>(Types.TimestampType.withoutZone(), DataType.TIMESTAMP, val ->
                    LocalDateTime.parse(val, GreenplumDateTime.DATETIME_FORMATTER)
            ),
            new SimpleIcebergConverter<>(Types.TimestampType.withoutZone(), DataType.TIMESTAMP_WITH_TIME_ZONE,
                    val -> LocalDateTime.parse(val, GreenplumDateTime.DATETIME_WITH_TIMEZONE_FORMATTER)
            ),
            new SimpleIcebergConverter<>(Types.TimestampType.withZone(), DataType.TIMESTAMP_WITH_TIME_ZONE, val ->
                    OffsetDateTime.parse(val, GreenplumDateTime.DATETIME_WITH_TIMEZONE_FORMATTER)
            ),
            // string converter
            new SimpleIcebergConverter<>(Types.StringType.get(), DataType.VARCHAR, Function.identity()),
            new SimpleIcebergConverter<>(Types.StringType.get(), DataType.TEXT, Function.identity())
    );

    Map<Pair<DataType, Type>, IcebergConverter<?>> convertersByTypes = converters.stream()
            .collect(toMap(c -> Pair.of(c.getCloudberryType(), c.getIcebergType()), Function.identity()));

    // it's introduced mostly because of decimal types that requires the same values of precision and scale
    Map<Pair<DataType, Type.TypeID>, IcebergConverter<?>> convertersByTypeIds = converters.stream()
            .filter(c -> Type.TypeID.DECIMAL.equals(c.getIcebergType().typeId()))
            .collect(toMap(c -> Pair.of(c.getCloudberryType(), c.getIcebergType().typeId()), Function.identity(), (prev, current) -> current));

    /**
     * Converts a float8 value to an Iceberg float, failing on the values that don't fit
     * like a cast to real does
     */
    static Float toFloat(String value) {
        double doubleValue = Double.parseDouble(value);
        float floatValue = (float) doubleValue;
        if (Float.isInfinite(floatValue) && !Double.isInfinite(doubleValue)) {
            throw new PxfRuntimeException(String.format("value out of range: overflow (%s does not fit into an Iceberg float)", value));
        }
        if (floatValue == 0.0f && doubleValue != 0.0) {
            throw new PxfRuntimeException(String.format("value out of range: underflow (%s does not fit into an Iceberg float)", value));
        }
        return floatValue;
    }

    static Integer toInteger(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new PxfRuntimeException(String.format("integer out of range (%d)", value));
        }
        return (int) value;
    }

    static Short toShort(long value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new PxfRuntimeException(String.format("smallint out of range (%d)", value));
        }
        return (short) value;
    }

    static EnumSet<DataType> getCloudberrySupportedTypes() {
        return EnumSet.copyOf(convertersByTypes.keySet().stream().map(Pair::getLeft).toList());
    }

    static IcebergConverter<?> getIcebergConverter(DataType cloudberryType, Type icebergType) {
        var converter = convertersByTypes.get(Pair.of(cloudberryType, icebergType));
        if(converter != null) {
            return converter;
        }
        converter = convertersByTypeIds.get(Pair.of(cloudberryType, icebergType.typeId()));
        if(converter != null) {
            return converter;
        }
        return unsupportedTypeConverter;
    }
}
