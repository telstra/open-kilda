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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.DeleteLinkRequest;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.response.DeleteLinkResponse;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.topology.nbworker.services.IslService;

import com.google.common.collect.Lists;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LinkOperationsBolt extends PersistenceOperationsBolt {

    private static final Logger logger = LoggerFactory.getLogger(LinkOperationsBolt.class);

    private transient IslService islService;

    public LinkOperationsBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        this.islService = new IslService(persistenceManager.getTransactionManager(), repositoryFactory);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request)
            throws IslNotFoundException, IllegalIslStateException {
        List<? extends InfoData> result = null;
        if (request instanceof GetLinksRequest) {
            result = getAllLinks();
        } else if (request instanceof LinkPropsGet) {
            result = getLinkProps((LinkPropsGet) request);
        } else if (request instanceof DeleteLinkRequest) {
            result = Lists.newArrayList(deleteLink((DeleteLinkRequest) request));
        } else {
            unhandledInput(tuple);
        }

        return result;
    }

    private List<IslInfoData> getAllLinks() {
        return islService.getAllIsls();
    }

    private List<LinkPropsData> getLinkProps(LinkPropsGet request) {
        // TODO: should be re-implemented in the scope of TE refactoring.
        return Collections.emptyList();
    }

    private DeleteLinkResponse deleteLink(DeleteLinkRequest request)
            throws IllegalIslStateException, IslNotFoundException {
        return new DeleteLinkResponse(islService.deleteIsl(
                request.getSrcSwitch(), request.getSrcPort(), request.getDstSwitch(), request.getDstPort()));
    }

    @Override
    Logger getLogger() {
        return logger;
    }
}
