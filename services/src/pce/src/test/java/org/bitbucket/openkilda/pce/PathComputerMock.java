package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.path.PathComputer;

import com.google.common.graph.MutableNetwork;

import java.util.Set;

public class PathComputerMock implements PathComputer {
    @Override
    public Set<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        System.out.println("getPath");
        return null;
    }

    @Override
    public Set<Isl> getPath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        System.out.println("getPath");
        return null;
    }

    @Override
    public Set<Isl> getPathIntersection(Set<Isl> firstPath, Set<Isl> secondPath) {
        System.out.println("getPathInterception");
        return null;
    }

    @Override
    public void updatePathBandwidth(Set<Isl> path, int bandwidth) {
        System.out.println("updatePathBandwidth");
        return;
    }

    @Override
    public void setNetwork(MutableNetwork<Switch, Isl> network) {
        System.out.println("setNetwork");
        return;
    }
}
