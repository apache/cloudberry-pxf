package org.apache.cloudberry.pxf.service.controller;

import org.apache.hadoop.conf.Configuration;
import org.apache.cloudberry.pxf.api.error.PxfRuntimeException;
import org.apache.cloudberry.pxf.api.io.Writable;
import org.apache.cloudberry.pxf.api.model.ConfigurationFactory;
import org.apache.cloudberry.pxf.api.model.Fragment;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.service.FragmenterService;
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

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class ReadServiceImplTest {

    @Mock
    private ConfigurationFactory mockConfigurationFactory;
    @Mock
    private BridgeFactory mockBridgeFactory;
    @Mock
    private SecurityService mockSecurityService;
    @Mock
    private FragmenterService mockFragmenterService;
    @Mock
    private MetricsReporter mockMetricReporter;
    @Mock
    private OutputStream mockOutputStream;
    @Mock
    private Configuration mockConfiguration;
    @Mock
    private List<Fragment> mockFragmentList;
    @Mock
    private Fragment mockFragment1, mockFragment2;
    @Mock
    private Bridge mockBridge1, mockBridge2;
    @Mock
    private Writable mockRecord1, mockRecord2, mockRecord3;
    @Mock
    private RequestContext mockContext;

    private ReadServiceImpl readService;
    private ActiveRequestRegistry activeRequestRegistry;

    @BeforeEach
    public void setup() throws Exception {
        when(mockConfigurationFactory.initConfiguration(any(), any(), any(), any())).thenReturn(mockConfiguration);
        when(mockFragmenterService.getFragmentsForSegment(mockContext)).thenReturn(mockFragmentList);
        when(mockSecurityService.doAs(same(mockContext), any())).thenAnswer(invocation -> {
            PrivilegedAction<OperationResult> action = invocation.getArgument(1);
            OperationResult result = action.run();
            return result;
        });

        activeRequestRegistry = new ActiveRequestRegistry();
        readService = new ReadServiceImpl(mockConfigurationFactory, mockBridgeFactory, mockSecurityService, mockFragmenterService, mockMetricReporter, activeRequestRegistry);
    }

    @Test
    public void testReadDataOneFragOneRecord() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenReturn(null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, mockMetricReporter);
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 5, mockContext);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragMultiRecordsReportBatch() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(2L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1, mockRecord2, null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, mockMetricReporter);
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 11, mockContext);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragMultiRecordsRemainder() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(5L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1, mockRecord2, null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, mockMetricReporter);
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 11, mockContext);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragMultiRecordsRemainderAfterBatch() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(2L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1, mockRecord2, mockRecord3, null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));
        doAnswer(writeTestData("Boo!")).when(mockRecord3).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, mockMetricReporter);
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 11, mockContext);
        inOrder.verify(mockOutputStream).write("Boo!".getBytes(StandardCharsets.UTF_8), 0, 4);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 4, mockContext);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragRecordsException() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(5L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenThrow(new Exception());
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        assertThrows(PxfRuntimeException.class, () -> readService.readData(mockContext, mockOutputStream));
        InOrder inOrder = inOrder(mockOutputStream, mockMetricReporter);
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 5, mockContext);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(false));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataMultiFragmentMultiRecord() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(2L);
        when(mockFragmentList.size()).thenReturn(2);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1, mockBridge2);

        // 1st frag
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenReturn(null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        // 2nd frag
        when(mockFragmentList.get(1)).thenReturn(mockFragment2);
        when(mockBridge2.beginIteration()).thenReturn(true);
        when(mockBridge2.getNext()).thenReturn(mockRecord2, mockRecord3, null);
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));
        doAnswer(writeTestData("Boo!")).when(mockRecord3).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, mockMetricReporter);
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 5, mockContext);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        inOrder.verify(mockOutputStream).write("Boo!".getBytes(StandardCharsets.UTF_8), 0, 4);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(mockMetricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 10, mockContext);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataZeroReportFrequency() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(0L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenReturn(null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, mockMetricReporter);
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataBeginIterationFalse() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(false);

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockBridge1, mockMetricReporter);
        inOrder.verify(mockBridge1).endIteration();
        inOrder.verify(mockMetricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), any(Duration.class), same(mockContext), eq(true));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCancelledLastFragmentIsReportedAsFailure() throws Exception {
        when(mockMetricReporter.getReportFrequency()).thenReturn(1L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockContext.getSegmentId()).thenReturn(0);
        when(mockContext.getGpSessionId()).thenReturn(42);
        when(mockContext.getDataSource()).thenReturn("test-resource");

        CountDownLatch getNextStarted = new CountDownLatch(1);
        CountDownLatch cancelBridge = new CountDownLatch(1);
        when(mockBridge1.getNext()).thenAnswer(invocation -> {
            getNextStarted.countDown();
            assertTrue(cancelBridge.await(5, TimeUnit.SECONDS), "cancel did not end the bridge");
            return null;
        });
        doAnswer(invocation -> {
            cancelBridge.countDown();
            return null;
        }).when(mockBridge1).endIteration();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> read = executor.submit(() -> readService.readData(mockContext, mockOutputStream));
            assertTrue(getNextStarted.await(5, TimeUnit.SECONDS), "read did not reach getNext");
            assertEquals(1, activeRequestRegistry.cancel(0, 42));

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> read.get(5, TimeUnit.SECONDS));
            assertTrue(exception.getCause() instanceof PxfRuntimeException);
            assertTrue(exception.getCause().getMessage().contains("cancelled by pxf_cancel_backend"));
        } finally {
            executor.shutdownNow();
        }
    }

    // helper for writing mock record to a mock output stream
    // mockOutputStream -> CountingOutputStream -> DataOutputStream
    // in order for the us to see the side-effect of CountingOutputStream,
    // we need to actually call the `write` method of DataOutputStream.
    private Answer writeTestData(String testData) {
        return invocation -> {
            DataOutputStream dos = invocation.getArgument(0);
            dos.write(testData.getBytes(StandardCharsets.UTF_8));
            return null;
        };
    }
}
