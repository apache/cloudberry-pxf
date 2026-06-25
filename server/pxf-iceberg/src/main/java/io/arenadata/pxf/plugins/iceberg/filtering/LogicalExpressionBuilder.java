package io.arenadata.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.greenplum.pxf.api.filter.Operator;

import java.util.Map;
import java.util.function.BinaryOperator;

public class LogicalExpressionBuilder extends AbstractExpressionBuilder {

    public static Map<Operator, BinaryOperator<Expression>> operators = Map.of(
            Operator.OR, Expressions::or,
            Operator.AND, Expressions::and
    );

    public LogicalExpressionBuilder(Operator operator) {
        super(operator, DataRequirement.builder().expressionsCount(2).build());
        assert operators.containsKey(operator);
    }

    @Override
    protected Expression buildAfterValidation(ExpressionData data) {
        return operators.get(getOperator()).apply(
                data.getExpressions().get(0),
                data.getExpressions().get(1)
        );
    }
}
