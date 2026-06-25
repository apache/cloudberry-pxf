package io.arenadata.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.greenplum.pxf.api.filter.Operator;

import java.util.Map;
import java.util.function.Function;

public class IsNullExpressionBuilder extends AbstractExpressionBuilder {

    public static Map<Operator, Function<String, Expression>> operators = Map.of(
            Operator.IS_NULL, Expressions::isNull,
            Operator.IS_NOT_NULL, columnName -> Expressions.not(Expressions.isNull(columnName))
    );

    public IsNullExpressionBuilder(Operator operator) {
        super(operator, DataRequirement.builder().columnName(true).build());
        assert operators.containsKey(operator);
    }

    @Override
    protected Expression buildAfterValidation(ExpressionData data) {
        return operators.get(getOperator()).apply(data.getColumnName());
    }
}
