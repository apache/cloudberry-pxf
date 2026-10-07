package org.apache.cloudberry.pxf.service.spring;

import org.apache.cloudberry.pxf.service.HttpHeaderDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class PxfContextMdcLogEnhancerFilterTest {

    PxfContextMdcLogEnhancerFilter filter;
    MockHttpServletRequest mockRequest;
    MockHttpServletResponse mockResponse;
    MockFilterChain mockFilterChain;

    @Mock
    MockedStatic<MDC> mdcMock;

    @BeforeEach
    void setup() {
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockFilterChain = new MockFilterChain();
        filter = new PxfContextMdcLogEnhancerFilter(new HttpHeaderDecoder());
    }

    @Test
    void testNonPxfContextRequest() throws ServletException, IOException {
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        // always removes
        mdcMock.verify(() -> MDC.remove("segmentId"));
        mdcMock.verify(() -> MDC.remove("sessionId"));
        mdcMock.verify(() -> MDC.remove("ssid"));
        mdcMock.verify(() -> MDC.remove("ccnt"));
        mdcMock.verifyNoMoreInteractions();
    }

    @Test
    void testPxfContextRequest() throws ServletException, IOException {

        mockRequest.addHeader("X-GP-XID", "transaction:id");
        mockRequest.addHeader("X-GP-SEGMENT-ID", "5");
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);

        mdcMock.verify(() -> MDC.put("sessionId", "transaction:id:default"));
        mdcMock.verify(() -> MDC.put("segmentId", "5"));
        mdcMock.verify(() -> MDC.put("ssid", null));
        mdcMock.verify(() -> MDC.put("ccnt", null));

        mdcMock.verify(() -> MDC.remove("segmentId"));
        mdcMock.verify(() -> MDC.remove("sessionId"));
        mdcMock.verify(() -> MDC.remove("ssid"));
        mdcMock.verify(() -> MDC.remove("ccnt"));
        mdcMock.verifyNoMoreInteractions();
    }

    @Test
    void testPxfContextRequestWithServerName() throws ServletException, IOException {

        mockRequest.addHeader("X-GP-XID", "transaction:id");
        mockRequest.addHeader("X-GP-OPTIONS-SERVER", "s3");
        mockRequest.addHeader("X-GP-SEGMENT-ID", "5");
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);

        mdcMock.verify(() -> MDC.put("sessionId", "transaction:id:s3"));
        mdcMock.verify(() -> MDC.put("segmentId", "5"));
        mdcMock.verify(() -> MDC.put("ssid", null));
        mdcMock.verify(() -> MDC.put("ccnt", null));

        mdcMock.verify(() -> MDC.remove("segmentId"));
        mdcMock.verify(() -> MDC.remove("sessionId"));
        mdcMock.verify(() -> MDC.remove("ssid"));
        mdcMock.verify(() -> MDC.remove("ccnt"));
        mdcMock.verifyNoMoreInteractions();
    }

    @Test
    void testPxfContextRequestWithGpSessionId() throws ServletException, IOException {

        mockRequest.addHeader("X-GP-XID", "transaction:id");
        mockRequest.addHeader("X-GP-SEGMENT-ID", "5");
        mockRequest.addHeader("X-GP-SESSION-ID", "12345");
        mockRequest.addHeader("X-GP-COMMAND-COUNT", "7");
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);

        mdcMock.verify(() -> MDC.put("sessionId", "transaction:id:default"));
        mdcMock.verify(() -> MDC.put("segmentId", "5"));
        mdcMock.verify(() -> MDC.put("ssid", "12345"));
        mdcMock.verify(() -> MDC.put("ccnt", "7"));

        mdcMock.verify(() -> MDC.remove("segmentId"));
        mdcMock.verify(() -> MDC.remove("sessionId"));
        mdcMock.verify(() -> MDC.remove("ssid"));
        mdcMock.verify(() -> MDC.remove("ccnt"));
        mdcMock.verifyNoMoreInteractions();
    }

    @Test
    void testPxfContextEncodedRequest() throws ServletException, IOException {

        mockRequest.addHeader("X-GP-ENCODED-HEADER-VALUES", "true");
        mockRequest.addHeader("X-GP-XID", "transaction%3Aid");
        mockRequest.addHeader("X-GP-SEGMENT-ID", "5");
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);

        mdcMock.verify(() -> MDC.put("sessionId", "transaction:id:default"));
        mdcMock.verify(() -> MDC.put("segmentId", "5"));
        mdcMock.verify(() -> MDC.put("ssid", null));
        mdcMock.verify(() -> MDC.put("ccnt", null));

        mdcMock.verify(() -> MDC.remove("segmentId"));
        mdcMock.verify(() -> MDC.remove("sessionId"));
        mdcMock.verify(() -> MDC.remove("ssid"));
        mdcMock.verify(() -> MDC.remove("ccnt"));
        mdcMock.verifyNoMoreInteractions();
    }
}