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

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.opentsdb.OpenTsdbMetricDatapoint;
import org.apache.storm.opentsdb.client.ClientResponse;

import java.io.IOException;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Map;

@Slf4j
public class OpenTsdbTelnetClient {
    private final Clock clock;

    private final String openTsdbHost;
    private final int openTsdbPort;

    private OpenTsdbTelnetSocketApi connect;

    public OpenTsdbTelnetClient(String openTsdbHost, int openTsdbPort) {
        this(Clock.systemUTC(), openTsdbHost, openTsdbPort);
    }

    public OpenTsdbTelnetClient(Clock clock, String openTsdbHost, int openTsdbPort) {
        this.clock = clock;

        this.openTsdbHost = openTsdbHost;
        this.openTsdbPort = openTsdbPort;

        ReconnectDelayProvider delayProvider = new ReconnectDelayProvider(Duration.ofSeconds(5));
        connect = new DummyConnect(clock, Duration.ZERO, delayProvider, new LinkedList<>());
    }

    public void write(OpenTsdbMetricDatapoint dataPoint) {
        write(makePutCommand(dataPoint));
    }

    private void write(String command) {
        try {
            // .write(...) call guaranteed to save command in write queue, so we can avoid write attempt after reconnect
            connect.write(command);
        } catch (CommunicationFailureException e) {
            log.info("Make new OpenTSDB telnet API connect");
            connect = makeConnect();
        }
    }

    public void cleanup() {
        connect.close();
    }

    private OpenTsdbTelnetSocketApi makeConnect() {
        try {
            return preserveWriteQueue(new Connect(new Socket(openTsdbHost, openTsdbPort)));
        } catch (IOException e) {
            log.error("Unable to connect to OpenTSDB server at {}:{} - {}", openTsdbHost, openTsdbPort, e);
            return makeDummyConnect(connect.getWriteQueue());
        }
    }

    private OpenTsdbTelnetSocketApi preserveWriteQueue(Connect replace) {
        try {
            replace.write(connect.getWriteQueue());
        } catch (CommunicationFailureException e) {
            log.debug("Force reconnect during connection queue transfer");
            return makeDummyConnect(replace.getWriteQueue());
        }

        return replace;
    }

    private OpenTsdbTelnetSocketApi makeDummyConnect(LinkedList<String> queue) {
        ReconnectDelayProvider delayProvider = connect.getDelayProvider();
        return new DummyConnect(clock, delayProvider.provide(), delayProvider, queue);
    }

    private String makePutCommand(OpenTsdbMetricDatapoint dataPoint) {
        StringBuilder command = new StringBuilder();
        command.append("put ");
        command.append(dataPoint.getMetric());
        command.append(' ');
        command.append(dataPoint.getTimestamp());
        command.append(' ');
        command.append(dataPoint.getValue());

        for (Map.Entry<String, String> entry : dataPoint.getTags().entrySet()) {
            command.append(' ');
            command.append(entry.getKey());
            command.append('=');
            command.append(entry.getValue());
        }

        return command.toString();
    }

    private ClientResponse.Details makeDummyResponse() {
        return new ClientResponse.Details();
    }
}
