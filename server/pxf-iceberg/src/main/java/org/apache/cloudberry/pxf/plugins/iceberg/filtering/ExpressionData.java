package org.apache.cloudberry.pxf.plugins.iceberg.filtering;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.types.Type;
import org.apache.cloudberry.pxf.api.io.DataType;

import java.util.List;

@Builder
@ToString
@Getter
public class ExpressionData {
    private String columnName;
    private DataType cloudberryType;
    private Type icebergType;
    private String scalarOperandValue;
    @Singular
    private List<Expression> expressions;
    private List<String> collectionData;
}
