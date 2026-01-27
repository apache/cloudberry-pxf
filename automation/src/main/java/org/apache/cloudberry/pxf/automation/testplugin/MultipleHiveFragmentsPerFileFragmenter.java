package org.apache.cloudberry.pxf.automation.testplugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.MetaStoreUtils;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.cloudberry.pxf.api.model.BaseFragmenter;
import org.apache.cloudberry.pxf.api.model.Fragment;
import org.apache.cloudberry.pxf.api.model.Metadata;
import org.apache.hadoop.security.PxfUserGroupInformation;
import org.apache.cloudberry.pxf.api.security.SecureLogin;
import org.apache.cloudberry.pxf.plugins.hive.HiveClientWrapper;
import org.apache.cloudberry.pxf.plugins.hive.HiveClientWrapper.HiveClientFactory;
import org.apache.cloudberry.pxf.plugins.hive.HiveFragmentMetadata;
import org.apache.cloudberry.pxf.plugins.hive.utilities.HiveUtilities;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Properties;

/**
 * Fragmenter which splits one file into multiple fragments. Helps to simulate a
 * case of big files.
 * <p>
 * inputData has to have following parameters:
 * TEST-FRAGMENTS-NUM - defines how many fragments will be returned for current file
 */
public class MultipleHiveFragmentsPerFileFragmenter extends BaseFragmenter {
    private static final Log LOG = LogFactory.getLog(MultipleHiveFragmentsPerFileFragmenter.class);

    private static final long SPLIT_SIZE = 1024;
    private JobConf jobConf;
    private HiveClientWrapper hiveClientWrapper;
    private HiveUtilities hiveUtilities;

    /**
     * Sets the {@link HiveClientWrapper} object
     *
     * @param hiveClientWrapper the hive client wrapper object
     */
    @Autowired
    public void setHiveClientWrapper(HiveClientWrapper hiveClientWrapper) {
        this.hiveClientWrapper = hiveClientWrapper;
    }

    /**
     * Sets the {@link HiveUtilities} object
     *
     * @param hiveUtilities the hive utilities object
     */
    @Autowired
    public void setHiveUtilities(HiveUtilities hiveUtilities) {
        this.hiveUtilities = hiveUtilities;
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        jobConf = new JobConf(configuration, MultipleHiveFragmentsPerFileFragmenter.class);
    }

    @Override
    public List<Fragment> getFragments() throws Exception {
        // TODO allowlist property
        String fragmentsOpt = context.getOption("TEST-FRAGMENTS-NUM");
        if (fragmentsOpt == null) {
            LOG.warn("TEST-FRAGMENTS-NUM not provided, defaulting to 1");
            fragmentsOpt = "1";
        }
        if (hiveUtilities == null) {
            hiveUtilities = new HiveUtilities();
        }
        if (hiveClientWrapper == null) {
            hiveClientWrapper = new HiveClientWrapper();
            hiveClientWrapper.setHiveUtilities(hiveUtilities);
            hiveClientWrapper.setHiveClientFactory(new HiveClientFactory());
            hiveClientWrapper.setSecureLogin(new SecureLogin(new PxfUserGroupInformation()));
        }
        int fragmentsNum = Integer.parseInt(fragmentsOpt);
        Metadata.Item tblDesc = hiveClientWrapper.extractTableFromName(context.getDataSource());
        Table tbl;
        try (HiveClientWrapper.MetaStoreClientHolder holder = hiveClientWrapper.initHiveClient(context, configuration)) {
            tbl = hiveClientWrapper.getHiveTable(holder.getClient(), tblDesc);
        }
        Properties properties = getSchema(tbl);

        for (int i = 0; i < fragmentsNum; i++) {
            String filePath = getFilePath(tbl);
            fragments.add(new Fragment(filePath, new HiveFragmentMetadata(i * SPLIT_SIZE, SPLIT_SIZE, properties)));
        }

        return fragments;
    }


    private static Properties getSchema(Table table) {
        return MetaStoreUtils.getSchema(table.getSd(), table.getSd(),
                table.getParameters(), table.getDbName(), table.getTableName(),
                table.getPartitionKeys());
    }

    private String getFilePath(Table tbl) throws Exception {

        StorageDescriptor descTable = tbl.getSd();

        InputFormat<?, ?> fformat = hiveUtilities.makeInputFormat(descTable.getInputFormat(), jobConf);

        FileInputFormat.setInputPaths(jobConf, new Path(descTable.getLocation()));

        InputSplit[] splits;
        try {
            splits = fformat.getSplits(jobConf, 1);
        } catch (org.apache.hadoop.mapred.InvalidInputException e) {
            LOG.debug("getSplits failed on " + e.getMessage());
            throw new RuntimeException("Unable to get file path for table.");
        }

        for (InputSplit split : splits) {
            FileSplit fsp = (FileSplit) split;
            return fsp.getPath().toString();
        }
        throw new RuntimeException("Unable to get file path for table.");
    }
}
