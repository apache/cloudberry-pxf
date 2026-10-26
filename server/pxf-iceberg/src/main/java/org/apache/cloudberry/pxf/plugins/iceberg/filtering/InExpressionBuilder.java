package org.apache.cloudberry.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.cloudberry.pxf.api.filter.Operator;

public class InExpressionBuilder extends AbstractExpressionBuilder {

    public InExpressionBuilder() {
        super(Operator.IN, DataRequirement.builder().columnName(true).dataType(true).collectionData(true).build());
    }

    @Override
    protected Expression buildAfterValidation(ExpressionData data) {
        if(!data.getCloudberryType().isArrayType()) {
            throw new IllegalStateException("Value should be array for operator " + getOperator());
        }
        var elementType = data.getCloudberryType().getTypeElem();
        return Expressions.in(
                data.getColumnName(),
                data.getCollectionData().stream()
                        .map(value -> toIcebergLiteral(elementType, data.getIcebergType(), value))
                        .toList()
        );

    }
}
