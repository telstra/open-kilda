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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.UUID;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * ResponseWrapper class is a wrapper of {@link HttpServletResponseWrapper} class. This will update
 * the response output stream to custom output stream, whose responsibility is to maintain a copy of
 * output stream in parallel to read the data any time without affecting the actual response data.
 * 
 * @author Gaurav Chugh
 *
 */
public class ResponseWrapper extends HttpServletResponseWrapper {

    private UUID id;
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private ServletOutputStreamWrapper streamWrapper;

    public ResponseWrapper(UUID requestId, HttpServletResponse response) {
        super(response);
        this.id = requestId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.ServletResponseWrapper#getOutputStream()
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("Writer has already been read on this response.");
        }

        if (outputStream == null) {
            outputStream = getResponse().getOutputStream();
            streamWrapper = new ServletOutputStreamWrapper(outputStream);
        }
        return streamWrapper;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.ServletResponseWrapper#getWriter()
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("Stream has already been read on this response.");
        }

        if (writer == null) {
            streamWrapper = new ServletOutputStreamWrapper(getResponse().getOutputStream());
            writer =
                    new PrintWriter(new OutputStreamWriter(streamWrapper, getResponse()
                            .getCharacterEncoding()), true);
        }

        return writer;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.ServletResponseWrapper#flushBuffer()
     */
    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        } else if (outputStream != null) {
            streamWrapper.flush();
        }
    }

    /**
     * Returns the data available in the stream.
     * 
     * @return byte[] having data available inside the stream.
     */
    public byte[] getData() {
        if (streamWrapper != null) {
            return streamWrapper.getData();
        }
        return new byte[0];
    }

    /**
     * Return the id of the request.
     * 
     * @return id.
     */
    public UUID getId() {
        return id;
    }
}
