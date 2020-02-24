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

package org.openkilda.wfm.topology.network.storm.bolt.isl.command;

import org.openkilda.messaging.info.discovery.InstallIslDefaultRulesResult;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.storm.bolt.isl.IslHandler;

public class IslDefaultRuleCreatedCommand extends IslCommand {
    private final InstallIslDefaultRulesResult payload;

    public IslDefaultRuleCreatedCommand(InstallIslDefaultRulesResult payload) {
        super(Endpoint.of(payload.getSrcSwitch(), payload.getSrcPort()),
                new IslReference(Endpoint.of(payload.getSrcSwitch(), payload.getSrcPort()),
                        Endpoint.of(payload.getDstSwitch(), payload.getDstPort())));
        this.payload = payload;
    }

    @Override
    public void apply(IslHandler handler) {
        handler.processIslRuleInstalled(getReference(), payload);
    }
}
