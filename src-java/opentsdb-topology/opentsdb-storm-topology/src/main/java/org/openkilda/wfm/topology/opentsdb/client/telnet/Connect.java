/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.opentsdb.client.telnet;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Slf4j
class Connect implements OpenTsdbTelnetSocketApi {
    private static final int READ_BUFFER_SIZE = 1024;
    private static final byte[] EOL = "\r\n".getBytes();

    private final Socket socket;

    @Getter
    private final LinkedList<String> writeQueue = new LinkedList<>();

    private byte[] readBuffer = new byte[READ_BUFFER_SIZE];
    private int readOffset = 0;

    public Connect(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void write(String entry) throws CommunicationFailureException {
        writeQueue.addLast(entry);
        flush();
    }

    @Override
    public void write(List<String> entries) throws CommunicationFailureException {
        writeQueue.addAll(entries);
        flush();
    }

    @Override
    public void close() {
        housekeeping();
    }

    @Override
    public ReconnectDelayProvider getDelayProvider() {
        return new ReconnectDelayProvider(
                Duration.ofMillis(100), Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(5),
                Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    private void flush() throws CommunicationFailureException {
        try {
            flushWriteQueue();
        } catch (IOException e) {
            log.error(String.format("Failed to push data into OpenTSDB: %s", e));
            housekeeping();
            throw new CommunicationFailureException();
        }
    }

    private void flushWriteQueue() throws IOException {
        reportErrorIfReceived();

        OutputStream stream = socket.getOutputStream();
        while (! writeQueue.isEmpty()) {
            String entry = writeQueue.getFirst();
            stream.write(entry.getBytes());
            stream.write(EOL);

            writeQueue.removeFirst();
        }
    }

    private void housekeeping() {
        try {
            socket.shutdownOutput();
        } catch (IOException e) {
            log.error(String.format("Unable to close OpenTSDB socket: %s", e));
        }

        for (String entry : readTillEof()) {
            reportError(entry);
        }
        String incomplete = getIncompleteResponse();
        if (incomplete != null) {
            reportError(incomplete + " (incomplete response)");
        }
    }

    private List<String> readIfAvail() throws IOException {
        List<String> results = new ArrayList<>();
        InputStream stream = socket.getInputStream();
        while (true) {
            int available = stream.available();
            if (available < 1) {
                break;
            }

            int free = READ_BUFFER_SIZE - readOffset;
            readOffset += stream.read(readBuffer, readOffset, Math.min(available, free));

            results.addAll(extractAllInputLines());
        }
        return results;
    }

    private List<String> readTillEof() {
        byte[] accumulate = ArrayUtils.subarray(readBuffer, 0, readOffset);
        readOffset = 0;

        try {
            InputStream stream = socket.getInputStream();
            while (! socket.isClosed()) {
                int count = stream.read(readBuffer, 0, readBuffer.length);
                accumulate = ArrayUtils.addAll(accumulate, ArrayUtils.subarray(readBuffer, 0, count));
            }
        } catch (IOException e) {
            log.error(String.format("Can't read OpenTSDB response: %s", e));
        }

        readBuffer = accumulate;
        return extractAllInputLines();
    }

    private List<String> extractAllInputLines() {
        List<String> results = new ArrayList<>();
        String entry;
        while ((entry = extractInputLine()) != null) {
            results.add(entry);
        }
        return results;
    }

    private String extractInputLine() {
        int eolOffset = findByteSequence(readBuffer, EOL, readOffset);
        if (eolOffset < 0) {
            return null;
        }

        final String chunk = new String(ArrayUtils.subarray(readBuffer, 0, eolOffset));

        byte[] stub = ArrayUtils.subarray(readBuffer, eolOffset + EOL.length, readBuffer.length);
        readBuffer = ArrayUtils.addAll(stub, new byte[READ_BUFFER_SIZE - stub.length]);

        readOffset -= stub.length;
        readOffset -= EOL.length;

        return chunk;
    }

    private String getIncompleteResponse() {
        if (0 < readOffset) {
            return new String(ArrayUtils.subarray(readBuffer, 0, readOffset));
        }
        return null;
    }

    private void reportErrorIfReceived() throws IOException {
        for (String entry : readIfAvail()) {
            reportError(entry);
        }
    }

    private void reportError(String error) {
        log.error("OTSDB server error response: {}", error);
    }

    private static int findByteSequence(byte[] data, byte[] needle, int stop) {
        int matchIndex = 0;
        int offset = 0;
        for (; offset < stop && matchIndex < needle.length; offset++) {
            if (data[offset] == needle[matchIndex]) {
                matchIndex += 1;
            } else {
                matchIndex = 0;
            }
        }

        if (matchIndex == needle.length && 0 < needle.length) {
            return offset - needle.length;
        }
        return -1;
    }
}
