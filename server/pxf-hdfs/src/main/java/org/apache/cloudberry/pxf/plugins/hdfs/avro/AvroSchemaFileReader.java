package org.apache.cloudberry.pxf.plugins.hdfs.avro;

import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.cloudberry.pxf.plugins.hdfs.HcfsType;

import java.io.IOException;

public interface AvroSchemaFileReader {
    Schema readSchema(Configuration configuration, String schemaName, HcfsType hcfsType, AvroUtilities.FileSearcher fileSearcher) throws IOException;
}
