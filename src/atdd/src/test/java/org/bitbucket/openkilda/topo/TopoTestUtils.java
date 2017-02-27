package org.bitbucket.openkilda.topo;

import java.util.concurrent.ConcurrentMap;

/**
 * Created by carmine on 2/27/17.
 */
public class TopoTestUtils {

    public static void main(String[] args) {
        ITopology t = TopologyBuilder.buildLinearTopo(5);
        System.out.println("t1 = " + t);

        t = TopologyBuilder.buildTreeTopo(3,3);
        System.out.println("t2 = " + t.printSwitchConnections());

        System.out.println("t2.size = " + t.getSwitches().keySet().size());

    }
}
