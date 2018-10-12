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

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.model.IslStatus;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/**
 * Convert {@link org.openkilda.model.Isl} to {@link IslInfoData} and back.
 */
@Mapper
public interface IslMapper {

    IslMapper INSTANCE = Mappers.getMapper(IslMapper.class);

    /**
     * Convert {@link org.openkilda.model.Isl} to {@link IslInfoData}.
     */
    default IslInfoData map(org.openkilda.model.Isl isl) {
        PathNode src = new PathNode();
        src.setSwitchId(new SwitchId(isl.getSrcSwitchId().toString()));
        src.setPortNo(isl.getSrcPort());
        src.setSegLatency(isl.getLatency());

        PathNode dst = new PathNode();
        dst.setSwitchId(new SwitchId(isl.getDestSwitchId().toString()));
        dst.setPortNo(isl.getDestPort());
        dst.setSegLatency(isl.getLatency());

        Long timeCreateMillis = Optional.ofNullable(isl.getTimeCreate()).map(Instant::toEpochMilli).orElse(null);
        Long timeModifyMillis = Optional.ofNullable(isl.getTimeModify()).map(Instant::toEpochMilli).orElse(null);
        return new IslInfoData(isl.getLatency(), Arrays.asList(src, dst), isl.getSpeed(), isl.getAvailableBandwidth(),
                map(isl.getStatus()), timeCreateMillis, timeModifyMillis);
    }


    org.openkilda.model.Isl map(IslInfoData isl);

    /**
     * Convert {@link org.openkilda.model.IslStatus} to {@link IslChangeType}.
     */
    default IslChangeType map(org.openkilda.model.IslStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case ACTIVE:
                return IslChangeType.DISCOVERED;
            case INACTIVE:
                return IslChangeType.FAILED;
            case MOVED:
                return IslChangeType.MOVED;
        }
    }

    /**
     * Convert {@link IslChangeType} to {@link org.openkilda.model.IslStatus}.
     */
    default org.openkilda.model.IslStatus map(IslChangeType status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case DISCOVERED:
            case CACHED:
            case OTHER_UPDATE:
                return IslStatus.ACTIVE;
            case FAILED:
                return IslStatus.INACTIVE;
            case MOVED:
                return IslStatus.MOVED;
        }
    }
}
