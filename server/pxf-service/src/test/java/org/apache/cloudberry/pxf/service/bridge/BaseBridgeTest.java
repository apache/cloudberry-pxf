package org.apache.cloudberry.pxf.service.bridge;

import org.apache.hadoop.conf.Configuration;
import org.apache.cloudberry.pxf.api.io.Writable;
import org.apache.cloudberry.pxf.api.model.Accessor;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.model.Resolver;
import org.apache.cloudberry.pxf.service.utilities.BasePluginFactory;
import org.apache.cloudberry.pxf.service.utilities.GSSFailureHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaseBridgeTest {

    private RequestContext context;
    private BasePluginFactory pluginFactory;
    private GSSFailureHandler failureHandler;

    @BeforeEach
    public void setup() {
        context = new RequestContext();
        context.setConfiguration(new Configuration());

        pluginFactory = new BasePluginFactory();
        failureHandler = new GSSFailureHandler();
    }

    @Test
    public void testContextConstructor() {
        context.setAccessor("org.apache.cloudberry.pxf.service.bridge.TestAccessor");
        context.setResolver("org.apache.cloudberry.pxf.service.bridge.TestResolver");

        TestBridge bridge = new TestBridge(pluginFactory, context, failureHandler);
        assertTrue(bridge.getAccessor() instanceof TestAccessor);
        assertTrue(bridge.getResolver() instanceof TestResolver);
    }

    @Test
    public void testContextConstructorUnknownAccessor() {
        context.setAccessor("org.apache.cloudberry.pxf.unknown-accessor");
        context.setResolver("org.apache.cloudberry.pxf.service.bridge.TestResolver");

        RuntimeException e = assertThrows(RuntimeException.class, () -> new TestBridge(pluginFactory, context, failureHandler));
        assertEquals("Class org.apache.cloudberry.pxf.unknown-accessor is not found", e.getMessage());
    }

    @Test
    public void testContextConstructorUnknownResolver() {
        context.setAccessor("org.apache.cloudberry.pxf.service.bridge.TestAccessor");
        context.setResolver("org.apache.cloudberry.pxf.unknown-resolver");

        Exception e = assertThrows(RuntimeException.class, () -> new TestBridge(pluginFactory, context, failureHandler));
        assertEquals("Class org.apache.cloudberry.pxf.unknown-resolver is not found", e.getMessage());
    }

    static class TestBridge extends BaseBridge {

        public TestBridge(BasePluginFactory pluginFactory, RequestContext context, GSSFailureHandler failureHandler) {
            super(pluginFactory, context, failureHandler);
        }

        @Override
        public boolean beginIteration() {
            return false;
        }

        @Override
        public Writable getNext() {
            return null;
        }

        @Override
        public boolean setNext(DataInputStream inputStream) {
            return false;
        }

        @Override
        public void endIteration() {
        }

        Accessor getAccessor() {
            return accessor;
        }

        Resolver getResolver() {
            return resolver;
        }
    }
}
