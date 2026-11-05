package org.apache.cloudberry.pxf.plugins.iceberg.filtering;

import org.apache.cloudberry.pxf.api.filter.ColumnIndexOperandNode;
import org.apache.cloudberry.pxf.api.filter.Node;
import org.apache.cloudberry.pxf.api.filter.Operator;
import org.apache.cloudberry.pxf.api.filter.OperatorNode;
import org.apache.cloudberry.pxf.api.filter.SupportedOperatorPruner;
import org.apache.iceberg.types.Type;

import java.util.EnumSet;
import java.util.Map;

/**
 * Prunes the operators that Iceberg evaluates differently from Cloudberry. Iceberg uses the
 * expression to skip whole files and row groups, so a pruned operator only costs performance,
 * while a pushed one with different semantics loses rows.
 * <ul>
 *   <li>strings are compared by code point, not by the column collation, so only the operators
 *   that don't depend on ordering are pushed</li>
 *   <li>floating point values follow IEEE semantics: NaN is not counted in the file bounds and
 *   -0.0 differs from 0.0, while in Cloudberry NaN is equal to itself and greater than any
 *   other value, so no comparison is pushed</li>
 * </ul>
 */
public class IcebergOperatorPruner extends SupportedOperatorPruner {

    private static final EnumSet<Operator> ORDERING_OPERATORS = EnumSet.of(
            Operator.LESS_THAN,
            Operator.LESS_THAN_OR_EQUAL,
            Operator.GREATER_THAN,
            Operator.GREATER_THAN_OR_EQUAL);

    private static final EnumSet<Operator> NULL_OPERATORS = EnumSet.of(
            Operator.IS_NULL,
            Operator.IS_NOT_NULL);

    private final Map<Integer, String> columnsByIndex;
    private final Map<String, Type> icebergTypes;

    public IcebergOperatorPruner(EnumSet<Operator> supportedOperators,
                                 Map<Integer, String> columnsByIndex,
                                 Map<String, Type> icebergTypes) {
        super(supportedOperators);
        this.columnsByIndex = columnsByIndex;
        this.icebergTypes = icebergTypes;
    }

    @Override
    public Node visit(Node node, int level) {
        if (node instanceof OperatorNode operatorNode && !operatorNode.getOperator().isLogical()) {
            Operator operator = operatorNode.getOperator();
            Type.TypeID typeId = getColumnTypeId(operatorNode);
            if (typeId == Type.TypeID.STRING && ORDERING_OPERATORS.contains(operator)) {
                LOG.debug("Operator {} is not pushed down for string columns", operator);
                return null;
            }
            if ((typeId == Type.TypeID.FLOAT || typeId == Type.TypeID.DOUBLE) && !NULL_OPERATORS.contains(operator)) {
                LOG.debug("Operator {} is not pushed down for floating point columns", operator);
                return null;
            }
        }
        return super.visit(node, level);
    }

    private Type.TypeID getColumnTypeId(OperatorNode operatorNode) {
        if (!(operatorNode.getLeft() instanceof ColumnIndexOperandNode columnOperand)) {
            return null;
        }
        Type type = icebergTypes.get(columnsByIndex.get(columnOperand.index()));
        return type != null ? type.typeId() : null;
    }
}
