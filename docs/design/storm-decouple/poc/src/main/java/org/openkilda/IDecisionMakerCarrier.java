package org.openkilda;

public interface IDecisionMakerCarrier {
    void discovered(SwitchId switchId, int portNo, SwitchId endSwitchId, int endPortNo, long currentTime);
    void failed(SwitchId switchId, int portNo, long currentTime);
}
