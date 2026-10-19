package org.apache.cloudberry.pxf.plugins.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.BasePlugin;
import org.apache.cloudberry.pxf.api.model.Resolver;
import org.apache.cloudberry.pxf.api.utilities.ColumnDescriptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.apache.cloudberry.pxf.plugins.iceberg.converters.IcebergConverters.getIcebergConverter;

public class IcebergResolver extends BasePlugin implements Resolver {

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
                        ).convertFromIcebergToGreengage(icebergRecord.getField(descriptor.columnName()))
                )).toList();
    }

    @Override
    public OneRow setFields(List<OneField> list) {
        Schema schema = (Schema) context.getMetadata();
        Record icebergRecord = GenericRecord.create(schema);

        for (int i = 0; i < list.size(); i++) {
            OneField field = list.get(i);
            ColumnDescriptor descriptor = context.getColumn(i);
            Type icebergType = findType(schema, descriptor);
            Object value = getIcebergConverter(descriptor.getDataType(), icebergType)
                    .convertFromGreengageToIceberg(field.val);
            icebergRecord.setField(descriptor.columnName(), adjustDecimalScale(value, icebergType));
        }
        return new OneRow(icebergRecord);
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
