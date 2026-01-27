package org.apache.cloudberry.pxf.service.bridge;

import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.Accessor;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.springframework.stereotype.Component;

public class TestAccessor implements Accessor {

    @Override
    public void afterPropertiesSet() {
    }

    @Override
    public boolean openForRead() {
        return false;
    }

    @Override
    public OneRow readNextObject() {
        return null;
    }

    @Override
    public void closeForRead() {

    }

    @Override
    public boolean openForWrite() {
        return false;
    }

    @Override
    public boolean writeNextObject(OneRow onerow) {
        return false;
    }

    @Override
    public void closeForWrite() {
    }

    @Override
    public void setRequestContext(RequestContext context) {
    }

}
