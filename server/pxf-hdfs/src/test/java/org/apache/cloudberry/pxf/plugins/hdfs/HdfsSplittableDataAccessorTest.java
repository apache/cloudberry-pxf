package org.apache.cloudberry.pxf.plugins.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.io.compress.SnappyCodec;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class HdfsSplittableDataAccessorTest {

    class TestAccessor extends HdfsSplittableDataAccessor {

        TestAccessor() {
            super(null);
        }

        @Override
        public boolean openForWrite() throws Exception {
            return false;
        }

        @Override
        public boolean writeNextObject(OneRow onerow) throws Exception {
            return false;
        }

        @Override
        public void closeForWrite() throws Exception {

        }

        @Override
        protected Object getReader(JobConf jobConf, InputSplit split) throws IOException {
            return null;
        }

        public CompressionCodec getCodecName(String name) {
            return getCodec(name);
        }
    }

    private TestAccessor accessor;

    @BeforeEach
    public void setup() {
        accessor = new TestAccessor();
        RequestContext context = new RequestContext();
        context.setConfiguration(new Configuration());
        accessor.setRequestContext(context);

    }

    @Test
    public void testCodecNoName() {
        String name = "some.bad.codec";
        Exception e = assertThrows(IllegalArgumentException.class,
                        () -> accessor.getCodecName(name));
        assertEquals("Compression codec some.bad.codec was not found.", e.getMessage());
    }

    @Test
    public void getCodecGzip() {
        String name = "org.apache.hadoop.io.compress.GzipCodec";
        CompressionCodec codec = accessor.getCodecName(name);
        assertNotNull(codec);
        assertTrue(codec instanceof GzipCodec);
    }

    @Test
    public void getCodecGzipShortName() {
        String name = "gzip";
        CompressionCodec codec = accessor.getCodecName(name);
        assertNotNull(codec);
        assertTrue(codec instanceof GzipCodec);
    }

    @Test
    public void getCodecDefaultShortName() {
        String name = "default";
        CompressionCodec codec = accessor.getCodecName(name);
        assertNotNull(codec);
        assertTrue(codec instanceof DefaultCodec);
    }

    @Test
    public void getCodecSnappyShortName() {
        String name = "snappy";
        CompressionCodec codec = accessor.getCodecName(name);
        assertNotNull(codec);
        assertTrue(codec instanceof SnappyCodec);
    }

    @Test
    public void getCodecUncompressed() {
        String name = "uncompressed";
        CompressionCodec codec = accessor.getCodecName(name);
        assertNull(codec);
    }
}
