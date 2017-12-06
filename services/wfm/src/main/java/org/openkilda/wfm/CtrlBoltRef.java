package org.openkilda.wfm;

import org.apache.storm.topology.BoltDeclarer;
import org.openkilda.wfm.ctrl.ICtrlBolt;

public class CtrlBoltRef {
    private final String boltId;
    private final ICtrlBolt bolt;
    private final BoltDeclarer declarer;

    public CtrlBoltRef(String boltId, ICtrlBolt bolt, BoltDeclarer declarer) {
        this.boltId = boltId;
        this.bolt = bolt;
        this.declarer = declarer;
    }

    public String getBoltId() {
        return boltId;
    }

    public ICtrlBolt getBolt() {
        return bolt;
    }

    public BoltDeclarer getDeclarer() {
        return declarer;
    }
}
