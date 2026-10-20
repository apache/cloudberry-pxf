package org.apache.cloudberry.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.cloudberry.pxf.api.filter.Operator;

import java.util.Map;
import java.util.function.BiFunction;

import static org.apache.cloudberry.pxf.plugins.iceberg.converters.IcebergConverters.getIcebergConverter;

public class ConditionExpressionBuilder extends AbstractExpressionBuilder {

    public static Map<Operator, BiFunction<String, Object, Expression>> operators = Map.of(
            Operator.EQUALS, Expressions::equal,
            Operator.NOT_EQUALS, Expressions::notEqual,
            Operator.GREATER_THAN, Expressions::greaterThan,
            Operator.GREATER_THAN_OR_EQUAL, Expressions::greaterThanOrEqual,
            Operator.LESS_THAN, Expressions::lessThan,
            Operator.LESS_THAN_OR_EQUAL, Expressions::lessThanOrEqual
    );

    public ConditionExpressionBuilder(Operator operator) {
        super(operator, DataRequirement.builder().columnName(true).dataType(true).build());
        assert operators.containsKey(operator);
    }

    @Override
    protected Expression buildAfterValidation(ExpressionData data) {
        return operators.get(getOperator()).apply(
            data.getColumnName(),
            getIcebergConverter(data.getCloudberryType(), data.getIcebergType()).convertFromCloudberryToIceberg(data.getScalarOperandValue())
        );
    }
}
