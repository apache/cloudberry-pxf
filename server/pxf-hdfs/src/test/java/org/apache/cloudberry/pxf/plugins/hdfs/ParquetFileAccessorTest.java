package org.apache.cloudberry.pxf.plugins.hdfs;

import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.cloudberry.pxf.api.io.DataType;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.api.utilities.ColumnDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParquetFileAccessorTest {
    ParquetFileAccessor accessor;
    RequestContext context;
    MessageType schema;

    @BeforeEach
    public void setup() {
        accessor = new ParquetFileAccessor();
        context = new RequestContext();
        context.setConfig("default");
        context.setUser("test-user");
        schema = new MessageType("hive_schema");
    }

    @Test
    public void testInitialize() {
        accessor.setRequestContext(context);
        assertNull(context.getMetadata());
    }

    @Test
    public void testGetTypeForColumnDescriptor_UUID() throws Exception {
        ColumnDescriptor uuidColumn = new ColumnDescriptor("id", DataType.UUID.getOID(), 0, "uuid", new Integer[]{});

        Method method = ParquetFileAccessor.class.getDeclaredMethod("getTypeForColumnDescriptor", ColumnDescriptor.class);
        method.setAccessible(true);
        Type result = (Type) method.invoke(accessor, uuidColumn);

        assertEquals("id", result.getName());
        assertTrue(result.isPrimitive());
        PrimitiveType primitiveType = result.asPrimitiveType();
        assertEquals(PrimitiveType.PrimitiveTypeName.BINARY, primitiveType.getPrimitiveTypeName());
        assertEquals(LogicalTypeAnnotation.stringType(), primitiveType.getLogicalTypeAnnotation());
    }

    @Test
    public void testGetTypeForColumnDescriptor_UUIDArray() throws Exception {
        ColumnDescriptor uuidArrayColumn = new ColumnDescriptor("ids", DataType.UUIDARRAY.getOID(), 0, "uuid[]", new Integer[]{});

        Method method = ParquetFileAccessor.class.getDeclaredMethod("getTypeForColumnDescriptor", ColumnDescriptor.class);
        method.setAccessible(true);
        Type result = (Type) method.invoke(accessor, uuidArrayColumn);

        assertEquals("ids", result.getName());
        // array types are wrapped in a list group
        assertTrue(result.asGroupType().isRepetition(Type.Repetition.OPTIONAL));
    }
}
