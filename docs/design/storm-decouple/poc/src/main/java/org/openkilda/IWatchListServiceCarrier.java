package org.openkilda;

public interface IWatchListServiceCarrier {
    void watchRemoved(SwitchId switchId, int portNo, long currentTime);
    void discoveryRequest(SwitchId switchId, int portNo, long currentTime);
}
