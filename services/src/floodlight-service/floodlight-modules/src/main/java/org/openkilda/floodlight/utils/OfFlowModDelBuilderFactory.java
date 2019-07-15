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

package org.openkilda.floodlight.utils;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.U64;

abstract class OfFlowModDelBuilderFactory extends OfFlowModBuilderFactory {
    public OfFlowModDelBuilderFactory() {
        super();
    }

    public OfFlowModDelBuilderFactory(int basePriority) {
        super(basePriority);
    }

    @Override
    public OFFlowMod.Builder makeBuilder(OFFactory of) {
        return of.buildFlowDeleteStrict()
                // During flow update/reroute new OF flows installed first and old removed after that. If cookieMask
                // field is empty, cookie value will not be used to match OF flow and as result new OF flow can be
                // removed (especially for ingress flow segments) because of same match+priority values for old and new
                // OF flows.
                .setCookieMask(U64.NO_MASK);
    }
}
