package org.apache.cloudberry.pxf.service.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class PxfContextMdcLogEnhancerDecoratorTest {

    TaskDecorator decorator;

    @Mock
    MockedStatic<MDC> mdcMock;

    @BeforeEach
    void setup() {
        decorator = new PxfContextMdcLogEnhancerDecorator();
    }

    @Test
    void testDecorateWithoutContext() throws InterruptedException {

        Map<String, String> contextMap = new HashMap<>();
        mdcMock.when(MDC::getCopyOfContextMap).thenReturn(contextMap);

        Thread thread = new Thread(() -> System.out.println("Run"));
        decorator.decorate(thread).run();
        thread.join();
        mdcMock.verify(MDC::getCopyOfContextMap);
        mdcMock.verify(() -> MDC.setContextMap(contextMap));
        mdcMock.verify(MDC::clear);
        mdcMock.verifyNoMoreInteractions();
    }

    @Test
    void testDecorateWithContext() throws InterruptedException {

        Map<String, String> contextMap = new HashMap<>();
        contextMap.put("foo", "bar");
        mdcMock.when(MDC::getCopyOfContextMap).thenReturn(contextMap);

        Thread thread = new Thread(() -> System.out.println("Run"));
        decorator.decorate(thread).run();
        thread.join();
        mdcMock.verify(MDC::getCopyOfContextMap);
        mdcMock.verify(() -> MDC.setContextMap(contextMap));
        mdcMock.verify(MDC::clear);
        mdcMock.verifyNoMoreInteractions();
    }
}