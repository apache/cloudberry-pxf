package org.apache.cloudberry.pxf.diagnostic;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.cloudberry.pxf.api.utilities.FragmentMetadata;

@NoArgsConstructor
@AllArgsConstructor
public class FilterVerifyFragmentMetadata implements FragmentMetadata {

    @Getter
    private String filter;
}
