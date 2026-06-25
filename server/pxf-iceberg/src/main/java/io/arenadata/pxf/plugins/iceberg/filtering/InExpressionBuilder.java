package io.arenadata.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.greenplum.pxf.api.filter.Operator;

import static io.arenadata.pxf.plugins.iceberg.converters.IcebergConverters.getIcebergConverter;

public class InExpressionBuilder extends AbstractExpressionBuilder {

    public InExpressionBuilder() {
        super(Operator.IN, DataRequirement.builder().columnName(true).dataType(true).collectionData(true).build());
    }

    @Override
    protected Expression buildAfterValidation(ExpressionData data) {
        if(!data.getGreengageType().isArrayType()) {
            throw new IllegalStateException("Value should be array for operator " + getOperator());
        }
        var converter = getIcebergConverter(data.getGreengageType().getTypeElem(), data.getIcebergType());
        return Expressions.in(
                data.getColumnName(),
                data.getCollectionData().stream().map(converter::convertFromGreengageToIceberg).toList()
        );

    }
}
