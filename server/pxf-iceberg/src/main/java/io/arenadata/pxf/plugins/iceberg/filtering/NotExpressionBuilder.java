package io.arenadata.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.greenplum.pxf.api.filter.Operator;

public class NotExpressionBuilder extends AbstractExpressionBuilder {

    public NotExpressionBuilder() {
        super(Operator.NOT, DataRequirement.builder().expressionsCount(1).build());
    }

    @Override
    protected Expression buildAfterValidation(ExpressionData data) {
        return Expressions.not(data.getExpressions().get(0));
    }
}
