package org.bitbucket.openkilda.wfm.topology.flow;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import com.google.common.graph.MutableNetwork;

import java.util.Collections;

public class PathComputerMock implements PathComputer {
    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow) {
        return emptyPath();
    }

    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(SwitchInfoData source, SwitchInfoData destination, int bandwidth) {
        return emptyPath();
    }

    @Override
    public PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network) {
        return null;
    }

    @Override
    public void init() {

    }

    private static ImmutablePair<PathInfoData, PathInfoData> emptyPath() {
        return new ImmutablePair<>(
                new PathInfoData(0L, Collections.emptyList()),
                new PathInfoData(0L, Collections.emptyList()));
    }
}
