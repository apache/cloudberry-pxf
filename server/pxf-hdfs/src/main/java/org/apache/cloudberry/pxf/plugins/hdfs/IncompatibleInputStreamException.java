package org.apache.cloudberry.pxf.plugins.hdfs;

public class IncompatibleInputStreamException extends Exception {

    public IncompatibleInputStreamException(Class actualClass) {
        super(String.format("Class %s is not a subclass of DFSInputStream", actualClass));
    }
}
