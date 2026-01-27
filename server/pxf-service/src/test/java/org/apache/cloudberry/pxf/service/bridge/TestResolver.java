package org.apache.cloudberry.pxf.service.bridge;

import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.model.Resolver;

import java.util.List;

public class TestResolver implements Resolver {

    @Override
    public void afterPropertiesSet() {
    }

    @Override
    public List<OneField> getFields(OneRow row) {
        return null;
    }

    @Override
    public OneRow setFields(List<OneField> record) {
        return null;
    }

    @Override
    public void setRequestContext(RequestContext context) {
    }
}
