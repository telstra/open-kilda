package org.openkilda.atdd.staging.service.traffexam.networkpool;

import java.util.*;

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
