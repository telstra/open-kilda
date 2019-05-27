/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchStatus;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Convert {@link Switch} to {@link SwitchInfoData} and back.
 */
@Mapper
public abstract class SwitchMapper {

    public static final SwitchMapper INSTANCE = Mappers.getMapper(SwitchMapper.class);

    /**
     * Convert {@link Switch} to {@link SwitchInfoData}.
     */
    public SwitchInfoData map(Switch sw) {
        if (sw == null) {
            return null;
        }

        SpeakerSwitchView switchView = SpeakerSwitchView.builder()
                .ofVersion(sw.getOfVersion())
                .description(SpeakerSwitchDescription.builder()
                        .datapath(sw.getOfDescriptionDatapath())
                        .hardware(sw.getOfDescriptionHardware())
                        .manufacturer(sw.getOfDescriptionManufacturer())
                        .serialNumber(sw.getOfDescriptionSerialNumber())
                        .software(sw.getOfDescriptionSoftware())
                        .build())
                .build();
        return new SwitchInfoData(sw.getSwitchId(), map(sw.getStatus()), sw.getAddress(), sw.getHostname(),
                sw.getDescription(), sw.getController(), sw.isUnderMaintenance(), switchView);
    }

    /**
     * Convert {@link SwitchInfoData} to {@link Switch}.
     */
    public Switch map(SwitchInfoData data) {
        if (data == null) {
            return null;
        }

        Switch sw = Switch.builder()
                .switchId(data.getSwitchId())
                .status(map(data.getState()))
                .address(data.getAddress())
                .hostname(data.getHostname())
                .description(data.getDescription())
                .controller(data.getController())
                .underMaintenance(data.isUnderMaintenance())
                .build();

        if (data.getSwitchView() != null) {
            sw.setOfVersion(data.getSwitchView().getOfVersion());
            if (data.getSwitchView().getDescription() != null) {
                sw.setOfDescriptionDatapath(data.getSwitchView().getDescription().getDatapath());
                sw.setOfDescriptionManufacturer(data.getSwitchView().getDescription().getManufacturer());
                sw.setOfDescriptionHardware(data.getSwitchView().getDescription().getHardware());
                sw.setOfDescriptionSoftware(data.getSwitchView().getDescription().getSoftware());
                sw.setOfDescriptionSerialNumber(data.getSwitchView().getDescription().getSerialNumber());
            }
        }

        return sw;
    }

    /**
     * Convert {@link SwitchStatus} to {@link SwitchChangeType}.
     */
    public SwitchChangeType map(SwitchStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case ACTIVE:
                return SwitchChangeType.ACTIVATED;
            case INACTIVE:
                return SwitchChangeType.DEACTIVATED;
            case REMOVED:
                return SwitchChangeType.REMOVED;
            default:
                throw new IllegalArgumentException("Unsupported Switch status: " + status);
        }
    }

    /**
     * Convert {@link SwitchChangeType} to {@link SwitchStatus}.
     */
    public SwitchStatus map(SwitchChangeType status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case ACTIVATED:
            case ADDED:
            case CHANGED:
            case VALIDATING:
            case CACHED:
                return SwitchStatus.ACTIVE;
            case DEACTIVATED:
                return SwitchStatus.INACTIVE;
            case REMOVED:
                return SwitchStatus.REMOVED;
            default:
                throw new IllegalArgumentException("Unsupported Switch status: " + status);
        }
    }
}
