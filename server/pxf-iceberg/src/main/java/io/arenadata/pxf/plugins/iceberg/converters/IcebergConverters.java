package io.arenadata.pxf.plugins.iceberg.converters;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.greenplum.pxf.api.GreenplumDateTime;
import org.greenplum.pxf.api.io.DataType;

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
            new SimpleIcebergConverter<>(Types.FloatType.get(), DataType.FLOAT8, Float::valueOf,
                    val -> Double.parseDouble(val.toString())
            ),
            // decimal converter
            new SimpleIcebergConverter<>(Types.DecimalType.of(10, 5), DataType.FLOAT8, Double::parseDouble),
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
            .collect(toMap(c -> Pair.of(c.getGreengageType(), c.getIcebergType()), Function.identity()));

    // it's introduced mostly because of decimal types that requires the same values of precision and scale
    Map<Pair<DataType, Type.TypeID>, IcebergConverter<?>> convertersByTypeIds = converters.stream()
            .filter(c -> Type.TypeID.DECIMAL.equals(c.getIcebergType().typeId()))
            .collect(toMap(c -> Pair.of(c.getGreengageType(), c.getIcebergType().typeId()), Function.identity(), (prev, current) -> current));

    static EnumSet<DataType> getGreengageSupportedTypes() {
        return EnumSet.copyOf(convertersByTypes.keySet().stream().map(Pair::getLeft).toList());
    }

    static IcebergConverter<?> getIcebergConverter(DataType greengageType, Type icebergType) {
        var converter = convertersByTypes.get(Pair.of(greengageType, icebergType));
        if(converter != null) {
            return converter;
        }
        converter = convertersByTypeIds.get(Pair.of(greengageType, icebergType.typeId()));
        if(converter != null) {
            return converter;
        }
        return unsupportedTypeConverter;
    }
}
