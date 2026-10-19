package org.apache.cloudberry.pxf.plugins.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.BasePlugin;
import org.apache.cloudberry.pxf.api.model.Resolver;
import org.apache.cloudberry.pxf.api.utilities.ColumnDescriptor;

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
                                schema.findType(descriptor.columnName())
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
            icebergRecord.setField(
                descriptor.columnName(),
                getIcebergConverter(
                        descriptor.getDataType(),
                        schema.findType(descriptor.columnName())
                ).convertFromGreengageToIceberg(field.val)
            );
        }
        return new OneRow(icebergRecord);
    }

}
