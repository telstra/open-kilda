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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsiderIngressRuleValidateResultAction extends SegmentValidateResultAction {
    public ConsiderIngressRuleValidateResultAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void onComplete(FlowCreateFsm fsm) {
        saveHistory(
                fsm, fsm.getCarrier(), fsm.getFlowId(), "Ingress segments validation have been completed",
                String.format("flowId: %s", fsm.getFlowId()));
        super.onComplete(fsm);
    }
}
