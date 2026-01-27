package org.apache.cloudberry.pxf.plugins.hdfs.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.mapred.FsInput;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.cloudberry.pxf.plugins.hdfs.HcfsType;

import java.io.File;
import java.io.IOException;

/**
 * Accessing the Avro file through the "unsplittable" API just to get the
 * schema. The splittable API (AvroInputFormat) which is the one we will be
 * using to fetch the records, does not support getting the Avro schema yet.
 *
 * @return the Avro schema
 * @throws IOException if I/O error occurred while accessing Avro schema file
 */
public class BinaryAvroSchemaFileReader implements AvroSchemaFileReader {
    @Override
    public Schema readSchema(Configuration configuration, String schemaName, HcfsType hcfsType, AvroUtilities.FileSearcher fileSearcher) throws IOException {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        DataFileReader<GenericRecord> fileReader = null;

        try {
            File file = fileSearcher.searchForFile(schemaName);
            if (file == null) {
                final Path path = new Path(hcfsType.getDataUri(configuration, schemaName));
                FsInput inStream = new FsInput(path, configuration);
                fileReader = new DataFileReader<>(inStream, datumReader);
            } else {
                fileReader = new DataFileReader<>(file, datumReader);
            }
            return fileReader.getSchema();
        } finally {
            if (fileReader != null) {
                fileReader.close();
            }
        }
    }
}
