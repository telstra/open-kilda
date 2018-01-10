package org.openkilda.security.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

/**
 * ServletOutputStreamWrapper is a wrapper of {@link ServletOutputStream}. The data passed is
 * written in one more secondary stream parallel to main stream to read any time, without affecting
 * the main stream.
 * 
 * @author Gaurav Chugh
 *
 */
public class ServletOutputStreamWrapper extends ServletOutputStream {

    private OutputStream outputStream;
    private ByteArrayOutputStream secondaryStream;

    public ServletOutputStreamWrapper(final OutputStream outputStream) {
        this.outputStream = outputStream;
        this.secondaryStream = new ByteArrayOutputStream(1024);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.io.OutputStream#write(int)
     */
    @Override
    public void write(final int data) throws IOException {
        outputStream.write(data);
        secondaryStream.write(data);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.io.OutputStream#write(byte[])
     */
    @Override
    public void write(final byte[] data) throws IOException {
        outputStream.write(data);
        secondaryStream.write(data);
    }

    /**
     * Returns the data from secondary stream.
     * 
     * @return byte[] having data available in the stream.
     */
    public byte[] getData() {
        return secondaryStream.toByteArray();
    }

    @Override
    public boolean isReady() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setWriteListener(WriteListener arg0) {
        // TODO Auto-generated method stub

    }
}
