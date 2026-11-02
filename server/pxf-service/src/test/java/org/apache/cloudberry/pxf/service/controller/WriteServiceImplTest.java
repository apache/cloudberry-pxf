package org.apache.cloudberry.pxf.service.controller;

import org.apache.hadoop.conf.Configuration;
import org.apache.cloudberry.pxf.api.model.ConfigurationFactory;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.service.MetricsReporter;
import org.apache.cloudberry.pxf.service.activity.ActiveRequestRegistry;
import org.apache.cloudberry.pxf.service.bridge.Bridge;
import org.apache.cloudberry.pxf.service.bridge.BridgeFactory;
import org.apache.cloudberry.pxf.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.security.PrivilegedAction;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class WriteServiceImplTest {

    @Mock
    private ConfigurationFactory mockConfigurationFactory;
    @Mock
    private BridgeFactory mockBridgeFactory;
    @Mock
    private SecurityService mockSecurityService;
    @Mock
    private MetricsReporter mockMetricReporter;
    @Mock
    private InputStream mockInputStream;
    @Mock
    private Configuration mockConfiguration;
    @Mock
    private Bridge mockBridge;
    @Mock
    private RequestContext mockContext;

    private WriteServiceImpl writeService;

    @BeforeEach
    public void setup() throws Exception {
        when(mockConfigurationFactory.initConfiguration(any(), any(), any(), any())).thenReturn(mockConfiguration);
        when(mockSecurityService.doAs(same(mockContext), any())).thenAnswer(invocation -> {
            PrivilegedAction<OperationResult> action = invocation.getArgument(1);
            OperationResult result = action.run();
            return result;
        });
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge);

        writeService = new WriteServiceImpl(mockConfigurationFactory, mockBridgeFactory, mockSecurityService, mockMetricReporter, new ActiveRequestRegistry());
    }

    @Test
    public void testWriteDataOneRecord() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockInputStream.read(any(), eq(0), eq(10))).thenReturn(4);
        doAnswer(readTestData(10))
                .doAnswer(invocation -> false)
                .when(mockBridge).setNext(any(DataInputStream.class));

        writeService.writeData(mockContext, mockInputStream);

        InOrder inOrder = inOrder(mockMetricReporter);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_RECEIVED, 1, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_RECEIVED, 4, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testWriteDataMultiRecordsReportBatch() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(2L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockInputStream.read(any(), eq(0), eq(10))).thenReturn(4, 6);
        doAnswer(readTestData(10))
                .doAnswer(readTestData(10))
                .doAnswer(invocation -> false)
                .when(mockBridge).setNext(any(DataInputStream.class));

        writeService.writeData(mockContext, mockInputStream);

        InOrder inOrder = inOrder(mockMetricReporter);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_RECEIVED, 2, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_RECEIVED, 10, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testWriteDataMultiRecordsRemainder() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(5L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockInputStream.read(any(), eq(0), eq(10))).thenReturn(4, 6);
        doAnswer(readTestData(10))
                .doAnswer(readTestData(10))
                .doAnswer(invocation -> false)
                .when(mockBridge).setNext(any(DataInputStream.class));

        writeService.writeData(mockContext, mockInputStream);

        InOrder inOrder = inOrder(mockMetricReporter);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_RECEIVED, 2, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_RECEIVED, 10, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testWriteDataMultiRecordsRemainderAfterBatch() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(2L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockInputStream.read(any(), eq(0), eq(10))).thenReturn(4, 6, 5);
        doAnswer(readTestData(10))
                .doAnswer(readTestData(10))
                .doAnswer(readTestData(10))
                .doAnswer(invocation -> false)
                .when(mockBridge).setNext(any(DataInputStream.class));

        writeService.writeData(mockContext, mockInputStream);

        InOrder inOrder = inOrder(mockMetricReporter);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_RECEIVED, 2, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_RECEIVED, 10, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_RECEIVED, 1, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_RECEIVED, 5, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testWriteDataRecordsException() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(5L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockInputStream.read(any(), eq(0), eq(10))).thenReturn(4);
        doAnswer(readTestData(10))
                .doThrow(new Exception())
                .when(mockBridge).setNext(any(DataInputStream.class));

        assertThrows(Exception.class, () -> writeService.writeData(mockContext, mockInputStream));
        InOrder inOrder = inOrder(mockMetricReporter);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_RECEIVED, 1, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_RECEIVED, 4, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testWriteDataZeroReportFrequency() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(0L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockInputStream.read(any(), eq(0), eq(10))).thenReturn(4);
        doAnswer(readTestData(10))
                .doAnswer(invocation -> false)
                .when(mockBridge).setNext(any(DataInputStream.class));

        writeService.writeData(mockContext, mockInputStream);

        verifyNoMoreInteractions(mockMetricReporter);
    }

    @Test
    public void testWriteDataBeginIterationException() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockBridge.beginIteration()).thenThrow(Exception.class);

        assertThrows(Exception.class, () -> writeService.writeData(mockContext, mockInputStream));
        verifyNoMoreInteractions(mockMetricReporter);
    }

    @Test
    public void testWriteDataSetNextFalse() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockBridge.setNext(any(DataInputStream.class))).thenReturn(false);

        writeService.writeData(mockContext, mockInputStream);

        InOrder inOrder = inOrder(mockInputStream, mockBridge, mockMetricReporter);
        inOrder.verify(mockInputStream).close();
        inOrder.verify(mockBridge).endIteration();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testWriteDataExceptionAbortsTheWrite() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockBridge.beginIteration()).thenReturn(true);
        when(mockBridge.setNext(any(DataInputStream.class))).thenThrow(new RuntimeException("bad record"));

        assertThrows(Exception.class, () -> writeService.writeData(mockContext, mockInputStream));

        verify(mockBridge).abortIteration();
        verify(mockBridge, never()).endIteration();
    }

    @Test
    public void testWriteDataIncompleteBodyAbortsTheWrite() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockBridge.beginIteration()).thenReturn(true);
        // the client went away in the middle of the chunked body
        when(mockInputStream.read()).thenThrow(new EOFException("Unexpected EOF read on the socket"));
        // like GPDBWritable, take an EOFException for the end of data
        when(mockBridge.setNext(any(DataInputStream.class))).thenAnswer(invocation -> {
            try {
                ((DataInputStream) invocation.getArgument(0)).readInt();
                return true;
            } catch (EOFException e) {
                return false;
            }
        });

        Exception e = assertThrows(Exception.class, () -> writeService.writeData(mockContext, mockInputStream));
        assertTrue(e.getMessage().contains("request body is incomplete"), e.getMessage());

        verify(mockBridge).abortIteration();
        verify(mockBridge, never()).endIteration();
    }

    // helper for reading mock stream to a mock input stream
    // mockInputStream -> CountingInputStream ->
    // in order for the us to see the side-effect of CountingInputStream,
    // we need to actually call the `read` method of DataInputStream.
    private Answer readTestData(int len) {
        return invocation -> {
            DataInputStream dis = invocation.getArgument(0);
            // needed to call the mockInputStream
            dis.read(null, 0, len);
            return true;
        };
    }
}
