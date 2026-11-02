package org.apache.cloudberry.pxf.service.controller;

import com.google.common.io.CountingInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.cloudberry.pxf.api.error.PxfRuntimeException;
import org.apache.cloudberry.pxf.api.model.ConfigurationFactory;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.utilities.Utilities;
import org.apache.cloudberry.pxf.service.MetricsReporter;
import org.apache.cloudberry.pxf.service.activity.ActiveRequestRegistry;
import org.apache.cloudberry.pxf.service.bridge.Bridge;
import org.apache.cloudberry.pxf.service.bridge.BridgeFactory;
import org.apache.cloudberry.pxf.service.security.SecurityService;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of the WriteService.
 */
@Service
@Slf4j
public class WriteServiceImpl extends BaseServiceImpl<OperationStats> implements WriteService {

    /**
     * Creates a new instance.
     *
     * @param configurationFactory  configuration factory
     * @param bridgeFactory         bridge factory
     * @param securityService       security service
     * @param metricsReporter       metrics reporter service
     * @param activeRequestRegistry registry of in-flight requests
     */
    public WriteServiceImpl(ConfigurationFactory configurationFactory,
                            BridgeFactory bridgeFactory,
                            SecurityService securityService,
                            MetricsReporter metricsReporter,
                            ActiveRequestRegistry activeRequestRegistry) {
        super("Write", configurationFactory, bridgeFactory, securityService, metricsReporter, activeRequestRegistry);
    }

    @Override
    public String writeData(RequestContext context, InputStream inputStream) throws Exception {
        OperationStats stats = processData(context, () -> readStream(context, inputStream));

        String censuredPath = Utilities.maskNonPrintables(context.getDataSource());
        String returnMsg = String.format("wrote %d records to %s", stats.getRecordCount(), censuredPath);
        log.debug(returnMsg);

        return returnMsg;
    }

    /**
     * Reads the input stream, iteratively submits data from the stream to created bridge.
     *
     * @param context     request context
     * @param inputStream input stream
     * @return operation statistics
     */
    private OperationResult readStream(RequestContext context, InputStream inputStream) {
        Bridge bridge = getBridge(context);

        OperationStats operationStats = new OperationStats(OperationStats.Operation.WRITE, metricsReporter, context);
        OperationResult operationResult = new OperationResult();

        // dataStream (and inputStream as the result) will close automatically at the end of the try block
        FailureTrackingInputStream trackingInputStream = new FailureTrackingInputStream(inputStream);
        CountingInputStream countingInputStream = new CountingInputStream(trackingInputStream);
        try (DataInputStream dataStream = new DataInputStream(countingInputStream)) {
            // expose the bridge so pxf_cancel_backend can end it mid-write
            attachBridge(bridge);
            // open the output file, returns true or throws an error
            bridge.beginIteration();
            while (!isCancelled() && bridge.setNext(dataStream)) {
                operationStats.reportCompletedRecord(countingInputStream.getCount());
            }
            if (isCancelled()) {
                throw new PxfRuntimeException(String.format(
                        "Write to resource %s cancelled by pxf_cancel_backend",
                        context.getDataSource()));
            }
            if (trackingInputStream.getFailure() != null) {
                // the record readers take an EOFException for the end of data
                throw new PxfRuntimeException(String.format(
                        "Write to resource %s was interrupted, the request body is incomplete",
                        context.getDataSource()), trackingInputStream.getFailure());
            }
        } catch (Exception e) {
            operationResult.setException(e);
        } finally {
            try {
                // don't let a failed or cancelled write commit the data written so far
                if (operationResult.getException() == null) {
                    bridge.endIteration();
                } else {
                    bridge.abortIteration();
                }
            } catch (Exception e) {
                if (operationResult.getException() == null) {
                    operationResult.setException(e);
                }
            } finally {
                // stop exposing the now-closed bridge to pxf_cancel_backend
                attachBridge(null);
            }

            // in the case where we fail to report a record due to an exception,
            // report the number of bytes that we were able to read before failure
            operationStats.setByteCount(countingInputStream.getCount());
            operationStats.flushStats();
            operationResult.setStats(operationStats);
        }

        return operationResult;
    }

    /**
     * Remembers an error reading the request body. When the client goes away in the middle of
     * a request, e.g. because its transaction was aborted, the chunked body ends with an
     * EOFException, which the record readers can't tell from the end of data.
     */
    private static class FailureTrackingInputStream extends FilterInputStream {

        private volatile IOException failure;

        FailureTrackingInputStream(InputStream in) {
            super(in);
        }

        IOException getFailure() {
            return failure;
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException e) {
                failure = e;
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return super.read(b, off, len);
            } catch (IOException e) {
                failure = e;
                throw e;
            }
        }

        @Override
        public long skip(long n) throws IOException {
            try {
                return super.skip(n);
            } catch (IOException e) {
                failure = e;
                throw e;
            }
        }
    }
}
