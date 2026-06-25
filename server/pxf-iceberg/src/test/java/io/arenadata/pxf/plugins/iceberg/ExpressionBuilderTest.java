package io.arenadata.pxf.plugins.iceberg;

import io.arenadata.pxf.plugins.iceberg.filtering.ExpressionBuilder;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.types.Types;
import org.greenplum.pxf.api.utilities.ColumnDescriptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.apache.iceberg.expressions.Expressions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionBuilderTest {

    private static Logger log = LoggerFactory.getLogger(ExpressionBuilderTest.class);

    private ExpressionBuilder builder = ExpressionBuilder.create(
        List.of(
            new ColumnDescriptor("id", 23, 0, "int4", null, true),
            new ColumnDescriptor("first_name", 25, 1, "text", null, true),
            new ColumnDescriptor("last_name", 25, 2, "text", null, true),
            new ColumnDescriptor("age", 23, 3, "int4", null, true)
        ), Map.of(
            "id", Types.IntegerType.get(),
            "first_name", Types.StringType.get(),
            "last_name", Types.StringType.get(),
            "age", Types.IntegerType.get()
        ));

    @ParameterizedTest
    @MethodSource("getTestData")
    public void checkOperatorSupport(String filterString, Expression expectedExpression) {
        log.info("Checking {} for {}", filterString, expectedExpression);
        var expression = builder.build(filterString);
        assertEquals(expression.toString(), expectedExpression.toString());
    }

    static List<Object[]> getTestData() {
        return List.of(
            new Object[] {"a0c23s1d1o2", greaterThan("id", 1) },
            new Object[] {"a0c23s1d2o2a1c25s6dfirst3o5l0", and(greaterThan("id", 2), equal("first_name", "first3")) },
            new Object[] {"a1o8", Expressions.isNull("first_name") },
            new Object[] {"a1o8l2", Expressions.not(Expressions.isNull("first_name")) },
            new Object[] {"a0m1007s1d1s1d2o10l2", Expressions.not(Expressions.in("id", List.of(1, 2))) },
            new Object[] {"a0m1007s1d1s1d2o10a3c23s2d26o2l0", and(in("id", List.of(1, 2)), greaterThan("age", 26)) }
        );
    }

}
