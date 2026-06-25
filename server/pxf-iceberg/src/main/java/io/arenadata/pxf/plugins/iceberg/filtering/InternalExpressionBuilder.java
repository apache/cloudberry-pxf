package io.arenadata.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.greenplum.pxf.api.filter.Operator;

public interface InternalExpressionBuilder {

    Operator getOperator();

    Expression build(ExpressionData data);

}
