package org.apache.cloudberry.pxf.service.serde;

import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.cloudberry.pxf.api.model.RequestContext;

import java.io.DataInput;
import java.util.Collections;
import java.util.List;

/**
 * A RecordReader that does not actually read data, but instead stores the whole input stream as the value of
 * the first and only field of the resulting record. The data will be read by downstream components from the input
 * stream directly.
 *
 * This is a performance optimization used, for example, by the StringPassResolver and LineBreakAccessor to not break
 * the incoming stream into records and instead just copy incoming bytes to the external system.
 */
public class StreamRecordReader extends BaseRecordReader {

    /**
     * Creates a new instance
     * @param context request context
     */
    public StreamRecordReader(RequestContext context) {
        super(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OneField> readRecord(DataInput input) {
        return Collections.singletonList(new OneField(DataType.BYTEA.getOID(), input));
    }
}
