package org.apache.cloudberry.pxf.plugins.iceberg.filtering;

import org.apache.iceberg.expressions.Expression;
import org.apache.cloudberry.pxf.api.filter.Operator;

public interface InternalExpressionBuilder {

    Operator getOperator();

    Expression build(ExpressionData data);

}
