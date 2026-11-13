package org.apache.cloudberry.pxf.plugins.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.error.PxfRuntimeException;
import org.apache.cloudberry.pxf.api.error.UnsupportedTypeException;
import org.apache.cloudberry.pxf.api.model.BasePlugin;
import org.apache.cloudberry.pxf.api.model.Resolver;
import org.apache.cloudberry.pxf.api.utilities.ColumnDescriptor;
import org.apache.cloudberry.pxf.plugins.iceberg.converters.IcebergConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.cloudberry.pxf.plugins.iceberg.converters.IcebergConverters.getIcebergConverter;
import static org.apache.cloudberry.pxf.plugins.iceberg.converters.IcebergConverters.unsupportedTypeConverter;

public class IcebergResolver extends BasePlugin implements Resolver {

    private List<IcebergConverter<?>> writeConverters;

    @Override
    public List<OneField> getFields(OneRow oneRow) {
        Schema schema = (Schema) context.getMetadata();
        Record icebergRecord = (Record) oneRow.getData();
        return context.getTupleDescription().stream()
                .map(descriptor -> new OneField(
                        descriptor.getDataType().getOID(),
                        getIcebergConverter(
                                descriptor.getDataType(),
                                findType(schema, descriptor)
                        ).convertFromIcebergToCloudberry(icebergRecord.getField(descriptor.columnName()))
                )).toList();
    }

    @Override
    public OneRow setFields(List<OneField> list) {
        Schema schema = (Schema) context.getMetadata();
        if (writeConverters == null) {
            writeConverters = getWriteConverters(schema);
        }
        Record icebergRecord = GenericRecord.create(schema);

        for (int i = 0; i < list.size(); i++) {
            OneField field = list.get(i);
            ColumnDescriptor descriptor = context.getColumn(i);
            Types.NestedField icebergField = schema.findField(descriptor.columnName());
            Object value = writeConverters.get(i).convertFromCloudberryToIceberg(field.val);
            if (value == null && icebergField.isRequired()) {
                throw new PxfRuntimeException(String.format("null value in column %s violates the not-null constraint of Iceberg table %s",
                        descriptor.columnName(), context.getDataSource()));
            }
            icebergRecord.setField(descriptor.columnName(), adjustDecimalScale(value, icebergField.type()));
        }
        return new OneRow(icebergRecord);
    }

    /**
     * Returns the converter of each column, failing for the columns that can't be written, so that
     * the error doesn't come from the file writer as a cast error, or only once a non-NULL value shows up.
     */
    private List<IcebergConverter<?>> getWriteConverters(Schema schema) {
        Set<String> columnNames = context.getTupleDescription().stream()
                .map(ColumnDescriptor::columnName)
                .collect(Collectors.toSet());
        schema.columns().stream()
                .filter(field -> field.isRequired() && !columnNames.contains(field.name()))
                .findFirst()
                .ifPresent(field -> {
                    throw new PxfRuntimeException(String.format("Required column %s of Iceberg table %s is missing in the table definition",
                            field.name(), context.getDataSource()));
                });
        return context.getTupleDescription().stream()
                .map(descriptor -> {
                    Type icebergType = findType(schema, descriptor);
                    IcebergConverter<?> converter = getIcebergConverter(descriptor.getDataType(), icebergType);
                    if (converter == unsupportedTypeConverter) {
                        throw new UnsupportedTypeException(String.format("Column %s of type %s cannot be written to Iceberg column of type %s",
                                descriptor.columnName(), descriptor.columnTypeName(), icebergType));
                    }
                    return converter;
                })
                .<IcebergConverter<?>>map(converter -> converter)
                .toList();
    }

    private Type findType(Schema schema, ColumnDescriptor descriptor) {
        Type type = schema.findType(descriptor.columnName());
        if (type == null) {
            throw new IllegalArgumentException(String.format("Column %s is not found in Iceberg table %s",
                    descriptor.columnName(), context.getDataSource()));
        }
        return type;
    }

    /**
     * Iceberg writers require decimal values to have exactly the scale of the column,
     * so round them the way a cast to numeric(p, s) does.
     */
    private static Object adjustDecimalScale(Object value, Type icebergType) {
        if (value instanceof BigDecimal decimal && icebergType instanceof Types.DecimalType decimalType
                && decimal.scale() != decimalType.scale()) {
            return decimal.setScale(decimalType.scale(), RoundingMode.HALF_UP);
        }
        return value;
    }

}
