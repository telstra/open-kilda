/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.pce.provider.Auth;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SwitchOperationsBolt extends NeoOperationsBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchOperationsBolt.class);

    public SwitchOperationsBolt(Auth neoAuth) {
        super(neoAuth);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, Session session) {
        List<? extends InfoData> result = null;
        if (request instanceof GetSwitchesRequest) {
            result = getSwitches(session);
        } else {
            unhandledInput(tuple);
        }

        return result;
    }

    private List<SwitchInfoData> getSwitches(Session session) {
        StatementResult result = session.run("MATCH (sw:switch) "
                + "RETURN "
                + "sw.name as name, "
                + "sw.address as address, "
                + "sw.hostname as hostname, "
                + "sw.description as description, "
                + "sw.controller as controller, "
                + "sw.state as state");
        List<SwitchInfoData> results = new ArrayList<>();
        for (Record record : result.list()) {
            SwitchInfoData sw = new SwitchInfoData();
            sw.setSwitchId(record.get("name").asString());
            sw.setAddress(record.get("address").asString());
            sw.setController(record.get("controller").asString());
            sw.setDescription(record.get("description").asString());
            sw.setHostname(record.get("hostname").asString());

            String status = record.get("state").asString();
            SwitchState st = "active".equals(status) ? SwitchState.ACTIVATED : SwitchState.DEACTIVATED;
            sw.setState(st);

            results.add(sw);
        }
        LOGGER.debug("Found switches: {}", results.size());

        return results;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("response", "correlationId"));
    }

    @Override
    Logger getLogger() {
        return LOGGER;
    }
}
