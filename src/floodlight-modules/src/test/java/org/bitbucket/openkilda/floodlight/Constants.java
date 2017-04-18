package org.bitbucket.openkilda.floodlight;

import org.bitbucket.openkilda.floodlight.switchmanager.OutputVlanType;

/**
 * Created by atopilin on 11/04/2017.
 */
public final class Constants {
    public static final String flowName = "test_flow";
    public static final String switchId = "00:00:00:00:00:00:00:01";
    public static final int inputPort = 1;
    public static final int outputPort = 2;
    public static final int transitVlanId = 100;
    public static final int outputVlanId = 200;
    public static final int inputVlanId = 300;
    public static final int bandwidth = 10000;
    public static final int meterId = 1;
    public static final OutputVlanType outputVlanTypeObject = OutputVlanType.REPLACE;
    public static final String outputVlanType = outputVlanTypeObject.toString();
    public static final int burstSize = 1024;
}
