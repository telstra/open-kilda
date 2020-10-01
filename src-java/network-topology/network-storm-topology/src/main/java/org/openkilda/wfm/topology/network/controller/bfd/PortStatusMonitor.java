/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.network.controller.bfd;

import org.openkilda.wfm.topology.network.controller.bfd.BfdPortFsm.BfdPortFsmContext;
import org.openkilda.wfm.topology.network.controller.bfd.BfdPortFsm.BfdPortFsmEvent;
import org.openkilda.wfm.topology.network.controller.bfd.BfdPortFsm.BfdPortFsmFactory;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.service.IBfdPortCarrier;

class PortStatusMonitor {
    private final BfdPortFsm consumer;

    private LinkStatus status = LinkStatus.DOWN;

    private boolean haveTransitions = false;

    PortStatusMonitor(BfdPortFsm consumer) {
        this.consumer = consumer;
    }

    void update(IBfdPortCarrier carrier, LinkStatus update) {
        haveTransitions |= status != update;
        status = update;

        propagate(carrier);
    }

    void cleanTransitions() {
        haveTransitions = false;
    }

    void pull(IBfdPortCarrier carrier) {
        if (haveTransitions) {
            propagate(carrier);
        }
    }

    private void propagate(IBfdPortCarrier carrier) {
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();
        BfdPortFsmEvent event;
        switch (status) {
            case UP:
                event = BfdPortFsmEvent.PORT_UP;
                break;
            case DOWN:
                event = BfdPortFsmEvent.PORT_DOWN;
                break;
            default:
                throw new IllegalArgumentException(String.format(
                        "Unsupported %s.%s link state. Can't handle event for %s",
                        LinkStatus.class.getName(), status, consumer.getLogicalEndpoint()));
        }
        BfdPortFsmFactory.EXECUTOR.fire(consumer, event, context);
    }
}
