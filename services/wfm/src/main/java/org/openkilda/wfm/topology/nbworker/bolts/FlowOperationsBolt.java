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
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.pce.provider.Auth;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FlowOperationsBolt extends NeoOperationsBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowOperationsBolt.class);

    public FlowOperationsBolt(Auth neoAuth) {
        super(neoAuth);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, Session session) {
        LOGGER.warn("Received unsupported Flow Operation");
        return null;
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
