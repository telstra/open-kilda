/* Copyright 2019 Telstra Open Source
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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.network.PathsInfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetPathsRequest;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.nbworker.services.PathsService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PathsBolt extends PersistenceOperationsBolt {
    private transient PathsService pathService;
    private final PathComputerConfig pathComputerConfig;

    public PathsBolt(PersistenceManager persistenceManager, PathComputerConfig pathComputerConfig) {
        super(persistenceManager);
        this.pathComputerConfig = pathComputerConfig;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        pathService = new PathsService(
                repositoryFactory, pathComputerConfig);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request, String correlationId) {
        List<? extends InfoData> result = null;
        if (request instanceof GetPathsRequest) {
            result = Collections.singletonList(getPaths((GetPathsRequest) request));
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private PathsInfoData getPaths(GetPathsRequest request) {
        try {
            return pathService.getPaths(request.getSrcSwitchId(), request.getDstSwitchId());
        } catch (RecoverableException e) {
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), "Database error.");
        } catch (SwitchNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Switch not found.");
        } catch (UnroutableFlowException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(),
                    String.format("Couldn't found any path from switch '%s' to switch '%s'.",
                            request.getSrcSwitchId(), request.getDstSwitchId()));
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields(ResponseSplitterBolt.FIELD_ID_RESPONSE,
                ResponseSplitterBolt.FIELD_ID_CORELLATION_ID));
    }
}
