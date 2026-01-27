package org.apache.cloudberry.pxf.plugins.hdfs.avro;

import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.cloudberry.pxf.plugins.hdfs.HcfsType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JsonAvroSchemaFileReader implements AvroSchemaFileReader {
    @Override
    public Schema readSchema(Configuration configuration, String schemaName, HcfsType hcfsType, AvroUtilities.FileSearcher fileSearcher) throws IOException {
        InputStream schemaStream = null;
        // try searching local disk (full path name and relative path in classpath) first.
        // by searching locally first we can avoid an extra call to external (fs.exists())
        try {
            File file = fileSearcher.searchForFile(schemaName);
            if (file == null) {
                Path path = new Path(hcfsType.getDataUri(configuration, schemaName));
                FileSystem fs = FileSystem.get(path.toUri(), configuration);
                schemaStream = new FSDataInputStream(fs.open(path));
            } else {
                schemaStream = new FileInputStream(file);
            }
            return (new Schema.Parser()).parse(schemaStream);
        } finally {
            if (schemaStream != null) {
                schemaStream.close();
            }
        }
    }
}
