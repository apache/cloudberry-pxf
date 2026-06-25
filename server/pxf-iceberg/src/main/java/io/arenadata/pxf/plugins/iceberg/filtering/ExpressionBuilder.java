package io.arenadata.pxf.plugins.iceberg.filtering;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.types.Type;
import org.greenplum.pxf.api.filter.*;
import org.greenplum.pxf.api.utilities.ColumnDescriptor;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.arenadata.pxf.plugins.iceberg.converters.IcebergConverters.getGreengageSupportedTypes;
import static java.util.stream.Collectors.toMap;


@Slf4j
public class ExpressionBuilder implements TreeVisitor {

    private static final TreeTraverser traverser = new TreeTraverser();

    private static final Map<Operator, InternalExpressionBuilder> expressionBuilders = Stream.of(
            new LogicalExpressionBuilder(Operator.OR),
            new LogicalExpressionBuilder(Operator.AND),
            new ConditionExpressionBuilder(Operator.EQUALS),
            new ConditionExpressionBuilder(Operator.NOT_EQUALS),
            new ConditionExpressionBuilder(Operator.GREATER_THAN),
            new ConditionExpressionBuilder(Operator.GREATER_THAN_OR_EQUAL),
            new ConditionExpressionBuilder(Operator.LESS_THAN),
            new ConditionExpressionBuilder(Operator.LESS_THAN_OR_EQUAL),
            new IsNullExpressionBuilder(Operator.IS_NULL),
            new IsNullExpressionBuilder(Operator.IS_NOT_NULL),
            new InExpressionBuilder(),
            new NotExpressionBuilder()
    ).collect(toMap(InternalExpressionBuilder::getOperator, Function.identity()));

    private static final TreeVisitor operatorPruner = new SupportedOperatorPruner(EnumSet.copyOf(expressionBuilders.keySet()));

    public static ExpressionBuilder create(List<ColumnDescriptor> columns, Map<String, Type> icebergTypes) {
        return new ExpressionBuilder(columns, icebergTypes);
    }

    private final Map<Integer, String> columnsByIndex;
    private final Map<String, Type> icebergTypes;
    private final TreeVisitor dataTypePruner;

    public ExpressionBuilder(List<ColumnDescriptor> columns, Map<String, Type> icebergTypes) {
        this.columnsByIndex = columns.stream().collect(
                toMap(ColumnDescriptor::columnIndex, ColumnDescriptor::columnName)
        );
        this.icebergTypes = icebergTypes;
        this.dataTypePruner = new SupportedDataTypePruner(columns, getGreengageSupportedTypes());
    }

    public Expression build(String filterString) {
        if(StringUtils.isEmpty(filterString)) {
            return Expressions.alwaysTrue();
        }
        try {
            Node root = new FilterParser().parse(filterString);
            traverser.traverse(root, dataTypePruner, operatorPruner, this);
            return this.getResult();
        } catch (Exception e) {
            log.warn("WHERE clause is omitted:", e);
        }
        return Expressions.alwaysTrue();

    }

    private final Deque<ExpressionData.ExpressionDataBuilder> stack = new ArrayDeque<>();
    @Getter
    private Expression result = Expressions.alwaysTrue();

    @Override
    public Node before(Node node, final int level) {
        if (node instanceof OperatorNode) {
            stack.push(ExpressionData.builder());
        }
        return node;
    }

    @Override
    public Node visit(Node node, final int level) {
        if (node instanceof OperatorNode) {
            return node;
        }
        var dataBuilder = stack.peek();
        if(dataBuilder == null) {
            throw new IllegalStateException("Wrong filter state");
        }
        if (node instanceof ScalarOperandNode scalarOperand) {
            dataBuilder.greengageType(scalarOperand.getDataType())
                    .scalarOperandValue(scalarOperand.getValue());
        } else if (node instanceof ColumnIndexOperandNode columnNode) {
            var columnName = columnsByIndex.get(columnNode.index());
            dataBuilder.columnName(columnName)
                    .icebergType(icebergTypes.get(columnName));
        } else if (node instanceof CollectionOperandNode collectionNode) {
            dataBuilder.collectionData(collectionNode.getData())
                    .greengageType(collectionNode.getDataType());
        }
        return node;
    }

    @Override
    public Node after(Node node, final int level) {
        if (node instanceof OperatorNode operatorNode) {
            var dataBuilder = stack.pop();
            if(dataBuilder == null) {
                throw new IllegalStateException("Wrong filter state");
            }
            Optional.ofNullable(expressionBuilders.get(operatorNode.getOperator()))
                    .map(expressionBuilder -> expressionBuilder.build(dataBuilder.build()))
                    .ifPresent(expression -> {
                        var parent = stack.peek();
                        if(parent == null) {
                            result = expression;
                        } else {
                            parent.expression(expression);
                        }
                    });
        }
        return node;
    }

}
