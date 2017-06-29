package org.bitbucket.openkilda.messaging.command;

import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

public final class Constants {
    public static final String flowName = "test_flow";
    public static final String switchId = "00:00:00:00:00:00:00:01";
    public static final int inputPort = 1;
    public static final int outputPort = 2;
    public static final int transitVlanId = 100;
    public static final int outputVlanId = 200;
    public static final int inputVlanId = 300;
    public static final long bandwidth = 10000;
    public static final long meterId = 1;
    public static final long burstSize = 1024;
    public static final OutputVlanType outputVlanType = OutputVlanType.REPLACE;
}
