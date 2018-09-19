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

package org.openkilda.testing.service.traffexam.networkpool;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class Inet4NetworkPool {
    private Inet4Network parentNetwork;
    private List<SupplyDescriptor> free;
    private List<SupplyDescriptor> supplied;
    private int prefix;
    private long nextIndex = 0;

    public Inet4NetworkPool(Inet4Network parentNetwork, int prefix) {
        this.parentNetwork = parentNetwork;
        this.prefix = prefix;

        free = new LinkedList<>();
        supplied = new LinkedList<>();
    }

    /**
     * Allocates a Inet4Network.
     */
    public Inet4Network allocate() throws Inet4ValueException {
        SupplyDescriptor item;

        try {
            item = free.remove(0);
        } catch (IndexOutOfBoundsException e) {
            item = new SupplyDescriptor(
                    parentNetwork.subnet(nextIndex, prefix), nextIndex);
            nextIndex++;
        }

        supplied.add(item);

        return item.network;
    }

    /**
     * Free the network.
     */
    public void free(Inet4Network network) throws Inet4ValueException {
        ListIterator<SupplyDescriptor> iter;
        SupplyDescriptor descriptor = null;

        for (iter = supplied.listIterator(); iter.hasNext(); ) {
            SupplyDescriptor item = iter.next();
            if (!network.equals(item.network)) {
                continue;
            }

            iter.remove();
            descriptor = item;
            break;
        }

        if (descriptor == null) {
            throw new Inet4ValueException("Network don't belong to this pool");
        }

        free.add(descriptor);
    }

    private final class SupplyDescriptor {
        Inet4Network network;
        long index;

        public SupplyDescriptor(Inet4Network network, long index) {
            this.network = network;
            this.index = index;
        }
    }
}
