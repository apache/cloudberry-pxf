package org.apache.cloudberry.pxf.service.spring;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.apache.cloudberry.pxf.service.HttpHeaderDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PxfServerRequestObservationConventionTest {

    private PxfServerRequestObservationConvention contributor;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    public void setup() {
        contributor = new PxfServerRequestObservationConvention(new HttpHeaderDecoder());
        mockRequest = new MockHttpServletRequest();
    }

    @Test
    public void testPxfWebMvcTagsContributor_pxfEndpoint_namedServer() {
        mockRequest.addHeader("X-GP-USER", "Alex");
        mockRequest.addHeader("X-GP-SEGMENT-ID", "5");
        mockRequest.addHeader("X-GP-OPTIONS-PROFILE", "test:text");
        mockRequest.addHeader("X-GP-OPTIONS-SERVER", "test_server");

        List<KeyValue> expectedTags = KeyValues.of("user", "Alex")
                .and("segment", "5")
                .and("profile", "test:text")
                .and("server", "test_server")
                .stream().collect(Collectors.toList());

        Iterable<KeyValue> tagsIterable = contributor.getLowCardinalityKeyValues(new ServerRequestObservationContext(mockRequest, new MockHttpServletResponse()));
        List<KeyValue> tags = StreamSupport.stream(tagsIterable.spliterator(), false).collect(Collectors.toList());

        assertTrue(tags.containsAll(expectedTags));
    }

    @Test
    public void testPxfWebMvcTagsContributor_pxfEndpoint_defaultServer() {
        mockRequest.addHeader("X-GP-USER", "Alex");
        mockRequest.addHeader("X-GP-SEGMENT-ID", "5");
        mockRequest.addHeader("X-GP-OPTIONS-PROFILE", "test:text");

        List<KeyValue> expectedTags = KeyValues.of("user", "Alex")
                .and("segment", "5")
                .and("profile", "test:text")
                .and("server", "default")
                .stream().collect(Collectors.toList());

        Iterable<KeyValue> tagsIterable = contributor.getLowCardinalityKeyValues(new ServerRequestObservationContext(mockRequest, new MockHttpServletResponse()));
        List<KeyValue> tags = StreamSupport.stream(tagsIterable.spliterator(), false).collect(Collectors.toList());

        assertTrue(tags.containsAll(expectedTags));
    }

    @Test
    public void testPxfWebMvcTagsContributor_pxfEndpoint_encoded() {
        mockRequest.addHeader("X-GP-ENCODED-HEADER-VALUES", "true");
        mockRequest.addHeader("X-GP-USER", "Alex");
        mockRequest.addHeader("X-GP-SEGMENT-ID", "5");
        mockRequest.addHeader("X-GP-OPTIONS-PROFILE", "test%3Atext");
        mockRequest.addHeader("X-GP-OPTIONS-SERVER", "test_server");

        List<KeyValue> expectedTags = KeyValues.of("user", "Alex")
                .and("segment", "5")
                .and("profile", "test:text")
                .and("server", "test_server")
                .stream().collect(Collectors.toList());

        Iterable<KeyValue> tagsIterable = contributor.getLowCardinalityKeyValues(new ServerRequestObservationContext(mockRequest, new MockHttpServletResponse()));
        List<KeyValue> tags = StreamSupport.stream(tagsIterable.spliterator(), false).collect(Collectors.toList());

        assertTrue(tags.containsAll(expectedTags));
    }

    @Test
    public void testPxfWebMvcTagsContributor_nonPxfEndpoint() {
        List<KeyValue> expectedTags = KeyValues.of("user", "unknown")
                .and("segment", "unknown")
                .and("profile", "unknown")
                .and("server", "unknown")
                .stream().collect(Collectors.toList());

        Iterable<KeyValue> tagsIterable = contributor.getLowCardinalityKeyValues(new ServerRequestObservationContext(mockRequest, new MockHttpServletResponse()));
        List<KeyValue> tags = StreamSupport.stream(tagsIterable.spliterator(), false).collect(Collectors.toList());

        assertTrue(tags.containsAll(expectedTags));
    }

}
