package org.apache.cloudberry.pxf.service.bridge;

import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.ReadVectorizedResolver;

import java.util.List;

public class TestReadVectorizedResolver extends TestResolver implements ReadVectorizedResolver {
    @Override
    public List<List<OneField>> getFieldsForBatch(OneRow batch) {
        return null;
    }
}
