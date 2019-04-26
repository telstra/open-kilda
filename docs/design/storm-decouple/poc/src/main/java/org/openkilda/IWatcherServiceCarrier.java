package org.openkilda;

public interface IWatcherServiceCarrier {

    void discovered(SwitchId switchId, int portNo, SwitchId endSwitchId, int endPortNo, long currentTime);
    void failed(SwitchId switchId, int portNo, long currentTime);
    void sendDiscovery(SwitchId switchId, int portNo, long packetNo, long currentTime);
}
