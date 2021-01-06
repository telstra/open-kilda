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

package org.openkilda.wfm.topology.opentsdb.bolts;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.opentsdb.client.telnet.OpenTsdbTelnetClient;

import org.apache.storm.opentsdb.OpenTsdbMetricDatapoint;
import org.apache.storm.opentsdb.bolt.ITupleOpenTsdbDatapointMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.util.List;

public class OpenTsdbTelnetProtocolBolt extends AbstractBolt {
    private final String host;
    private final int port;
    private final List<? extends ITupleOpenTsdbDatapointMapper> datapointMappers;

    private transient OpenTsdbTelnetClient client;

    public OpenTsdbTelnetProtocolBolt(
            String host, int port, List<? extends ITupleOpenTsdbDatapointMapper> datapointMappers) {
        this.host = host;
        this.port = port;
        this.datapointMappers = datapointMappers;
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        for (ITupleOpenTsdbDatapointMapper mapper : datapointMappers) {
            OpenTsdbMetricDatapoint point = mapper.getMetricPoint(input);
            client.write(point);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        // no output streams here
    }

    @Override
    protected void init() {
        super.init();
        client = new OpenTsdbTelnetClient(host, port);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        client.cleanup();
    }
}
