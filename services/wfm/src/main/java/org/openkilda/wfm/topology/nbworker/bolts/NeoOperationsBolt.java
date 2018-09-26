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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.pce.provider.Auth;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.IslExistsException;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.neo4j.driver.v1.AccessMode;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public abstract class NeoOperationsBolt extends AbstractBolt {

    private Driver driver;
    private final Auth neoAuth;

    NeoOperationsBolt(Auth neoAuth) {
        this.neoAuth = neoAuth;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.driver = neoAuth.getDriver();
        super.prepare(stormConf, context, collector);
    }

    protected void handleInput(Tuple input) {
        BaseRequest request = (BaseRequest) input.getValueByField("request");
        final String correlationId = input.getStringByField("correlationId");
        getLogger().debug("Received operation request");

        try (Session session = driver.session(request.isReadRequest() ? AccessMode.READ : AccessMode.WRITE)) {
            List<? extends InfoData> result = processRequest(input, request, session);
            getOutput().emit(input, new Values(result, correlationId));
        } catch (IllegalArgumentException e) {
            ErrorData errorData = new ErrorData(ErrorType.PARAMETERS_INVALID,
                    e.getMessage(),
                    "Invalid parameters.");
            ErrorMessage result = new ErrorMessage(errorData,
                    System.currentTimeMillis(), correlationId, Destination.NORTHBOUND);
            getOutput().emit(input, new Values(result, correlationId));
        } catch (IslExistsException e) {
            ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND,
                    e.getMessage(),
                    "ISL does not exist.");
            ErrorMessage result = new ErrorMessage(errorData,
                    System.currentTimeMillis(), correlationId, Destination.NORTHBOUND);
            getOutput().emit(input, new Values(result, correlationId));
        }
    }

    abstract List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, Session session);

    abstract Logger getLogger();

    @Override
    public void cleanup() {
        driver.close();
    }
}
