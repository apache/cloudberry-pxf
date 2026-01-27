package org.apache.cloudberry.pxf.automation.testplugin;

import org.apache.cloudberry.pxf.api.model.BaseFragmenter;
import org.apache.cloudberry.pxf.api.model.Fragment;

import java.util.List;

public class FaultyGUCFragmenter extends BaseFragmenter {

	@Override
	public List<Fragment> getFragments() throws Exception {
		throw new Exception(getClass().getSimpleName() + ": login " +
							context.getLogin() + " secret " +
							context.getSecret());
	}
}
