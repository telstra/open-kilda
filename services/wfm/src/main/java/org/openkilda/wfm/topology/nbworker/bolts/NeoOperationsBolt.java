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
import org.openkilda.persistence.neo4j.Neo4jConfig;
import org.openkilda.persistence.neo4j.Neo4jTransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.impl.RepositoryFactoryImpl;
import org.openkilda.wfm.AbstractBolt;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public abstract class NeoOperationsBolt extends AbstractBolt {

    private final Neo4jConfig neo4jConfig;
    protected Neo4jTransactionManager transactionManager;
    private RepositoryFactory repositoryFactory;

    NeoOperationsBolt(Neo4jConfig neo4jConfig) {
        this.neo4jConfig = neo4jConfig;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        transactionManager = new Neo4jTransactionManager(neo4jConfig);
        repositoryFactory = new RepositoryFactoryImpl(transactionManager);

        super.prepare(stormConf, context, collector);
    }

    protected void handleInput(Tuple input) {
        BaseRequest request = (BaseRequest) input.getValueByField("request");
        final String correlationId = input.getStringByField("correlationId");
        getLogger().debug("Received operation request");

        List<? extends InfoData> result = processRequest(input, request, repositoryFactory);
        getOutput().emit(input, new Values(result, correlationId));
    }

    abstract List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request,
                                                     RepositoryFactory repositoryFactory);

    abstract Logger getLogger();
}
