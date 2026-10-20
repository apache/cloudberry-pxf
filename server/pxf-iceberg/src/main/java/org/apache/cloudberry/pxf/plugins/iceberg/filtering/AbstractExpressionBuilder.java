package org.apache.cloudberry.pxf.plugins.iceberg.filtering;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.types.Type;
import org.apache.cloudberry.pxf.api.filter.Operator;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.springframework.util.CollectionUtils;

import java.util.EnumSet;

import static org.apache.cloudberry.pxf.plugins.iceberg.converters.IcebergConverters.getIcebergConverter;

@RequiredArgsConstructor
public abstract class AbstractExpressionBuilder implements InternalExpressionBuilder {
    @Getter
    private final Operator operator;
    private final DataRequirement requirement;

    @Override
    public final Expression build(ExpressionData data) {
        validate(data);
        return buildAfterValidation(data);
    }

    protected abstract Expression buildAfterValidation(ExpressionData data);

    private static final EnumSet<DataType> INTEGRAL_TYPES = EnumSet.of(DataType.SMALLINT, DataType.INTEGER, DataType.BIGINT);

    /**
     * Converts a filter constant to an Iceberg literal value. The constant's type can differ from the column's:
     * for "bigint_col < 0" Cloudberry keeps 0 as int4 (it has cross-type integer operators). Integer constants on
     * int and long columns are therefore passed as a Long, which Iceberg narrows to the column type itself,
     * handling values out of the int range.
     */
    protected static Object toIcebergLiteral(DataType constantType, Type icebergType, Object value) {
        if (value != null && INTEGRAL_TYPES.contains(constantType)
                && (icebergType.typeId() == Type.TypeID.INTEGER || icebergType.typeId() == Type.TypeID.LONG)) {
            return Long.parseLong(value.toString());
        }
        return getIcebergConverter(constantType, icebergType).convertFromCloudberryToIceberg(value);
    }

    private void validate(ExpressionData data) {
        if(requirement.columnName && StringUtils.isEmpty(data.getColumnName())) {
            throw new IllegalStateException("Column should be defined for operator " + getOperator());
        }
        if(requirement.dataType && data.getCloudberryType() == null) {
            throw new IllegalStateException("Value data type should be defined for operator " + getOperator());
        }
        if(requirement.collectionData && CollectionUtils.isEmpty(data.getCollectionData())) {
            throw new IllegalStateException("Collection data should not be empty for operator " + getOperator());
        }
        if(requirement.expressionsCount > 0 && data.getExpressions().size() != requirement.expressionsCount) {
            throw new IllegalStateException("There should be " + requirement.expressionsCount + " expressions for operator " + getOperator());
        }

    }

    @Builder
    static class DataRequirement {
        @Builder.Default
        private boolean columnName = false;
        @Builder.Default
        private boolean dataType = false;
        @Builder.Default
        private boolean scalarOperandValue = false;
        @Builder.Default
        private int expressionsCount = 0;
        @Builder.Default
        private boolean collectionData = false;
    }
}
