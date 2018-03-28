package org.openkilda.floodlight.converter;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchInfoExtendedData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converter of floodlight switch representation {@link net.floodlightcontroller.core.IOFSwitch}.
 */
public final class IOFSwitchConverter {

    /**
     * Transforms {@link IOFSwitch} to object that is used throughout kilda in all components.
     * @param sw switch data.
     * @param eventType switch state.
     * @return converted switch.
     */
    public static SwitchInfoData buildSwitchInfoData(IOFSwitch sw, SwitchState eventType) {
        String switchId = sw.getId().toString();
        InetSocketAddress address = (InetSocketAddress) sw.getInetAddress();
        InetSocketAddress controller =(InetSocketAddress) sw.getConnectionByCategory(
                LogicalOFMessageCategory.MAIN).getRemoteInetAddress();

        return new SwitchInfoData(
                switchId,
                eventType,
                String.format("%s:%d",
                        address.getHostString(),
                        address.getPort()),
                address.getHostName(),
                String.format("%s %s %s",
                        sw.getSwitchDescription().getManufacturerDescription(),
                        sw.getOFFactory().getVersion().toString(),
                        sw.getSwitchDescription().getSoftwareDescription()),
                controller.getHostString());
    }

    /**
     * Transforms {@link IOFSwitch} to object that is used throughout kilda in all components.
     * @param sw switch data.
     * @param eventType switch state.
     * @param flowStats installed flows.
     * @return converted switch.
     */
    public static SwitchInfoExtendedData buildSwitchInfoDataExtended(IOFSwitch sw, SwitchState eventType,
            OFFlowStatsReply flowStats) {
        SwitchInfoData switchInfoData = buildSwitchInfoData(sw, eventType);

        List<FlowEntry> flows = flowStats.getEntries().stream()
                .map(OFFlowStatsConverter::toFlowEntry)
                .collect(Collectors.toList());

        return new SwitchInfoExtendedData(switchInfoData, flows);
    }

    private IOFSwitchConverter() {
    }
}
