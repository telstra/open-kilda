/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
        return false;
    }

    @Override
    public void setWriteListener(WriteListener arg0) {
    }
}
