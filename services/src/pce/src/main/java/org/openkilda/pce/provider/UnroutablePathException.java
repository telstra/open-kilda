package org.openkilda.pce.provider;

public class UnroutablePathException extends Exception {
    private final String srcSwitch;
    private final String dstSwitch;
    private final int bandwidth;

    public UnroutablePathException(String srcSwitch, String dstSwitch, int bandwidth) {
        super(String.format("Can't make flow from %s to %s (bandwidth=%d", srcSwitch, dstSwitch, bandwidth));

        this.srcSwitch = srcSwitch;
        this.dstSwitch = dstSwitch;
        this.bandwidth = bandwidth;
    }

    public String getSrcSwitch() {
        return srcSwitch;
    }

    public String getDstSwitch() {
        return dstSwitch;
    }

    public int getBandwidth() {
        return bandwidth;
    }
}
