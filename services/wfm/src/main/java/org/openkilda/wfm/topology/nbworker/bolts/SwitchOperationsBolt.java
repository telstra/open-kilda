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
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.persistence.neo4j.Neo4jConfig;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.mappers.SwitchMapper;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class SwitchOperationsBolt extends NeoOperationsBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchOperationsBolt.class);

    public SwitchOperationsBolt(Neo4jConfig neo4jConfig) {
        super(neo4jConfig);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, RepositoryFactory repositoryFactory) {
        List<? extends InfoData> result = null;
        if (request instanceof GetSwitchesRequest) {
            result = getSwitches(repositoryFactory.getSwitchRepository());
        } else {
            unhandledInput(tuple);
        }

        return result;
    }

    private List<SwitchInfoData> getSwitches(SwitchRepository switchRepository) {
        return switchRepository.findAll().stream()
                .map(SwitchMapper.INSTANCE::map)
                .collect(Collectors.toList());
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
