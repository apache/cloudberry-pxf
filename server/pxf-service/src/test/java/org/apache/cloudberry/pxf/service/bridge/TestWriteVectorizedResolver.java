package org.apache.cloudberry.pxf.service.bridge;

import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.WriteVectorizedResolver;

import java.util.List;

public class TestWriteVectorizedResolver extends TestResolver implements WriteVectorizedResolver {
    @Override
    public int getBatchSize() {
        return 0;
    }

    @Override
    public OneRow setFieldsForBatch(List<List<OneField>> records) {
        return null;
    }
}
