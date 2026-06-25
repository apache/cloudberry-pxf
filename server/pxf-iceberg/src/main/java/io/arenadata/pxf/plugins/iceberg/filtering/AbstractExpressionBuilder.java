package io.arenadata.pxf.plugins.iceberg.filtering;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.iceberg.expressions.Expression;
import org.greenplum.pxf.api.filter.Operator;
import org.springframework.util.CollectionUtils;

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

    private void validate(ExpressionData data) {
        if(requirement.columnName && StringUtils.isEmpty(data.getColumnName())) {
            throw new IllegalStateException("Column should be defined for operator " + getOperator());
        }
        if(requirement.dataType && data.getGreengageType() == null) {
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
