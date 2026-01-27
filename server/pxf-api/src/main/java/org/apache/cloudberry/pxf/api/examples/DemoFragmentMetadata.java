package org.apache.cloudberry.pxf.api.examples;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.cloudberry.pxf.api.utilities.FragmentMetadata;

@NoArgsConstructor
public class DemoFragmentMetadata implements FragmentMetadata {

    @Getter
    @Setter
    private String path;

    public DemoFragmentMetadata(String path) {
        this.path = path;
    }
}
