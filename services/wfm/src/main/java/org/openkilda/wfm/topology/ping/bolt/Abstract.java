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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.Group;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.tuple.Tuple;

abstract class Abstract extends org.openkilda.wfm.AbstractBolt {
    public static final String FIELD_ID_PING = "ping";
    public static final String FIELD_ID_PING_GROUP = "ping.group";

    protected PingContext pullPingContext(Tuple input) throws PipelineException {
        return pullValue(input, FIELD_ID_PING, PingContext.class);
    }

    protected Group pullPingGroup(Tuple input) throws PipelineException {
        return pullValue(input, FIELD_ID_PING_GROUP, Group.class);
    }
}
