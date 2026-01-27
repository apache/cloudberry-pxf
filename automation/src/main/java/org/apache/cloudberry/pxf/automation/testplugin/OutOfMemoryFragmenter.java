package org.apache.cloudberry.pxf.automation.testplugin;

import org.apache.cloudberry.pxf.api.model.BaseFragmenter;
import org.apache.cloudberry.pxf.api.model.Fragment;

import java.util.LinkedList;
import java.util.List;

public class OutOfMemoryFragmenter extends BaseFragmenter {

    @Override
    public List<Fragment> getFragments() throws Exception {
        List<Object> objectList = new LinkedList<>();
        while (true) {
            byte[] a = new byte[10 * 1024 * 1024];
            objectList.add(a);
        }
    }
}
