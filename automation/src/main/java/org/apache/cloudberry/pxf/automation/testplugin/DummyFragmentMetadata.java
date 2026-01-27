package org.apache.cloudberry.pxf.automation.testplugin;

import org.apache.cloudberry.pxf.api.utilities.FragmentMetadata;

public class DummyFragmentMetadata implements FragmentMetadata {

    private String s;

    public DummyFragmentMetadata() {
    }

    public DummyFragmentMetadata(String s) {
        this.s = s;
    }

    public String getS() {
        return s;
    }
}
