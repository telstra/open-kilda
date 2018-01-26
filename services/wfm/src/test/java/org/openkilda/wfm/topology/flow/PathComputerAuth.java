package org.openkilda.wfm.topology.flow;

import org.openkilda.pce.provider.Auth;

public class PathComputerAuth implements Auth {
    @Override
    public PathComputerMock connect() {
        return new PathComputerMock();
    }
}
