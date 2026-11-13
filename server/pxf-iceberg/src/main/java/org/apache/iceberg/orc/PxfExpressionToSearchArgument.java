package org.apache.iceberg.orc;

import com.google.common.collect.ImmutableSet;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument.TruthValue;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentFactory;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.iceberg.expressions.Bound;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Type.TypeID;
import org.apache.orc.TypeDescription;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

class PxfExpressionToSearchArgument
        extends ExpressionVisitors.BoundVisitor<PxfExpressionToSearchArgument.Action> {

    static SearchArgument convert(Expression expr, TypeDescription readSchema) {
        Map<Integer, String> idToColumnName =
                ORCSchemaUtil.idToOrcName(ORCSchemaUtil.convert(readSchema));
        SearchArgument.Builder builder = SearchArgumentFactory.newBuilder();
        ExpressionVisitors.visit(expr, new PxfExpressionToSearchArgument(builder, idToColumnName))
                .invoke();
        return builder.build();
    }

    private static final Set<TypeID> UNSUPPORTED_TYPES =
            ImmutableSet.of(
                    TypeID.BINARY, TypeID.FIXED, TypeID.UUID, TypeID.STRUCT, TypeID.MAP, TypeID.LIST);

    private final SearchArgument.Builder builder;
    private final Map<Integer, String> idToColumnName;

    private PxfExpressionToSearchArgument(
            SearchArgument.Builder builder, Map<Integer, String> idToColumnName) {
        this.builder = builder;
        this.idToColumnName = idToColumnName;
    }

    @Override
    public Action alwaysTrue() {
        return () -> this.builder.literal(TruthValue.YES);
    }

    @Override
    public Action alwaysFalse() {
        return () -> this.builder.literal(TruthValue.NO);
    }

    @Override
    public Action not(Action child) {
        return () -> {
            this.builder.startNot();
            child.invoke();
            this.builder.end();
        };
    }

    @Override
    public Action and(Action leftChild, Action rightChild) {
        return () -> {
            this.builder.startAnd();
            leftChild.invoke();
            rightChild.invoke();
            this.builder.end();
        };
    }

    @Override
    public Action or(Action leftChild, Action rightChild) {
        return () -> {
            this.builder.startOr();
            leftChild.invoke();
            rightChild.invoke();
            this.builder.end();
        };
    }

    @Override
    public <T> Action isNull(Bound<T> expr) {
        return () ->
                this.builder.isNull(idToColumnName.get(expr.ref().fieldId()), type(expr.ref().type()));
    }

    @Override
    public <T> Action notNull(Bound<T> expr) {
        return () ->
                this.builder
                        .startNot()
                        .isNull(idToColumnName.get(expr.ref().fieldId()), type(expr.ref().type()))
                        .end();
    }

    @Override
    public <T> Action isNaN(Bound<T> expr) {
        return () ->
                this.builder.equals(
                        idToColumnName.get(expr.ref().fieldId()),
                        type(expr.ref().type()),
                        literal(expr.ref().type(), getNaNForType(expr.ref().type())));
    }

    private Object getNaNForType(Type type) {
        switch (type.typeId()) {
            case FLOAT:
                return Float.NaN;
            case DOUBLE:
                return Double.NaN;
            default:
                throw new IllegalArgumentException("Cannot get NaN value for type " + type.typeId());
        }
    }

    @Override
    public <T> Action notNaN(Bound<T> expr) {
        return () -> {
            this.builder.startOr();
            isNull(expr).invoke();
            this.builder.startNot();
            isNaN(expr).invoke();
            this.builder.end();
            this.builder.end();
        };
    }

    @Override
    public <T> Action lt(Bound<T> expr, Literal<T> lit) {
        return () ->
                this.builder.lessThan(
                        idToColumnName.get(expr.ref().fieldId()),
                        type(expr.ref().type()),
                        literal(expr.ref().type(), lit.value()));
    }

    @Override
    public <T> Action ltEq(Bound<T> expr, Literal<T> lit) {
        return () ->
                this.builder.lessThanEquals(
                        idToColumnName.get(expr.ref().fieldId()),
                        type(expr.ref().type()),
                        literal(expr.ref().type(), lit.value()));
    }

    @Override
    public <T> Action gt(Bound<T> expr, Literal<T> lit) {
        return () ->
                this.builder
                        .startNot()
                        .lessThanEquals(
                                idToColumnName.get(expr.ref().fieldId()),
                                type(expr.ref().type()),
                                literal(expr.ref().type(), lit.value()))
                        .end();
    }

    @Override
    public <T> Action gtEq(Bound<T> expr, Literal<T> lit) {
        return () ->
                this.builder
                        .startNot()
                        .lessThan(
                                idToColumnName.get(expr.ref().fieldId()),
                                type(expr.ref().type()),
                                literal(expr.ref().type(), lit.value()))
                        .end();
    }

    @Override
    public <T> Action eq(Bound<T> expr, Literal<T> lit) {
        return () ->
                this.builder.equals(
                        idToColumnName.get(expr.ref().fieldId()),
                        type(expr.ref().type()),
                        literal(expr.ref().type(), lit.value()));
    }

    @Override
    public <T> Action notEq(Bound<T> expr, Literal<T> lit) {
        return () -> {
            this.builder.startOr();
            isNull(expr).invoke();
            this.builder.startNot();
            eq(expr, lit).invoke();
            this.builder.end();
            this.builder.end();
        };
    }

    @Override
    public <T> Action in(Bound<T> expr, Set<T> literalSet) {
        return () ->
                this.builder.in(
                        idToColumnName.get(expr.ref().fieldId()),
                        type(expr.ref().type()),
                        literalSet.stream().map(lit -> literal(expr.ref().type(), lit)).toArray(Object[]::new));
    }

    @Override
    public <T> Action notIn(Bound<T> expr, Set<T> literalSet) {
        return () -> {
            this.builder.startOr();
            isNull(expr).invoke();
            this.builder.startNot();
            in(expr, literalSet).invoke();
            this.builder.end();
            this.builder.end();
        };
    }

    @Override
    public <T> Action startsWith(Bound<T> expr, Literal<T> lit) {
        return () -> this.builder.literal(TruthValue.YES_NO_NULL);
    }

    @Override
    public <T> Action notStartsWith(Bound<T> expr, Literal<T> lit) {
        return () -> this.builder.literal(TruthValue.YES_NO_NULL);
    }

    @Override
    public <T> Action predicate(BoundPredicate<T> pred) {
        if (UNSUPPORTED_TYPES.contains(pred.ref().type().typeId())
                || !(pred.term() instanceof BoundReference)) {
            return () -> this.builder.literal(TruthValue.YES_NO_NULL);
        } else {
            return super.predicate(pred);
        }
    }

    @FunctionalInterface
    interface Action {
        void invoke();
    }

    private PredicateLeaf.Type type(Type icebergType) {
        switch (icebergType.typeId()) {
            case BOOLEAN:
                return PredicateLeaf.Type.BOOLEAN;
            case INTEGER:
            case LONG:
            case TIME:
                return PredicateLeaf.Type.LONG;
            case FLOAT:
            case DOUBLE:
                return PredicateLeaf.Type.FLOAT;
            case DATE:
                return PredicateLeaf.Type.DATE;
            case TIMESTAMP:
                return PredicateLeaf.Type.TIMESTAMP;
            case STRING:
                return PredicateLeaf.Type.STRING;
            case DECIMAL:
                return PredicateLeaf.Type.DECIMAL;
            default:
                throw new UnsupportedOperationException(
                        "Type " + icebergType + " not supported in ORC SearchArguments");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Object literal(Type icebergType, T icebergLiteral) {
        switch (icebergType.typeId()) {
            case BOOLEAN:
            case LONG:
            case TIME:
            case DOUBLE:
                return icebergLiteral;
            case INTEGER:
                return ((Integer) icebergLiteral).longValue();
            case FLOAT:
                return ((Float) icebergLiteral).doubleValue();
            case STRING:
                return icebergLiteral.toString();
            case DATE:
                return Date.valueOf(LocalDate.ofEpochDay((Integer) icebergLiteral));
            case TIMESTAMP:
                long microsFromEpoch = (Long) icebergLiteral;
                return Timestamp.from(
                        Instant.ofEpochSecond(
                                Math.floorDiv(microsFromEpoch, 1_000_000),
                                Math.floorMod(microsFromEpoch, 1_000_000) * 1_000L));
            case DECIMAL:
                return new HiveDecimalWritable(HiveDecimal.create((BigDecimal) icebergLiteral, false));
            default:
                throw new UnsupportedOperationException(
                        "Type " + icebergType + " not supported in ORC SearchArguments");
        }
    }
}
