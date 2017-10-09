/* Copyright 2017 Telstra Open Source
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

package org.openkilda.topology.service.impl;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.topology.domain.Isl;
import org.openkilda.topology.domain.Switch;
import org.openkilda.topology.domain.repository.IslRepository;
import org.openkilda.topology.domain.repository.SwitchRepository;
import org.openkilda.topology.service.IslService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages operations on links.
 */
@Service
@Transactional
public class IslServiceImpl implements IslService {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(IslServiceImpl.class);

    /**
     * Switch repository.
     */
    @Autowired
    private SwitchRepository switchRepository;

    /**
     * Isl repository.
     */
    @Autowired
    private IslRepository islRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public void discoverLink(final IslInfoData data) {
        logger.debug("Isl discover: isl={}", data);

        PathNode sourceNode = data.getPath().get(0);
        PathNode destinationNode = data.getPath().get(1);

        Isl isl = islRepository.findIsl(sourceNode.getSwitchId(), sourceNode.getPortNo(),
                destinationNode.getSwitchId(), destinationNode.getPortNo());

        logger.debug("Isl relationship found: {}", isl);

        if (isl == null) {
            Switch sourceSwitch = switchRepository.findByName(sourceNode.getSwitchId());
            Switch destinationSwitch = switchRepository.findByName(destinationNode.getSwitchId());

            if (sourceSwitch == null || destinationSwitch == null) {
                logger.error("Could not find switch: source={}, destination={}", sourceSwitch, destinationSwitch);
                return;
            }

            isl = new Isl(sourceSwitch, destinationSwitch, sourceNode.getSwitchId(), sourceNode.getPortNo(),
                    destinationNode.getSwitchId(), destinationNode.getPortNo(), data.getLatency(), data.getSpeed(), 0L);

            //TODO: replace queries on Spring auto generated
            //islRepository.save(isl);

            logger.debug("Isl relationship create: isl={}", isl);

            islRepository.creteIsl(sourceSwitch.getName(), destinationSwitch.getName(), sourceNode.getSwitchId(),
                    sourceNode.getPortNo(), destinationNode.getSwitchId(), destinationNode.getPortNo(),
                    data.getLatency(), data.getSpeed(), 0L);
        } else {
            islRepository.updateLatency(sourceNode.getSwitchId(), sourceNode.getPortNo(),
                    destinationNode.getSwitchId(), destinationNode.getPortNo(), data.getLatency());

            logger.debug("Isl relationship update: latency={}", data.getLatency());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Isl getLink(final IslInfoData data) {
        logger.debug("Isl get: isl={}", data);

        PathNode sourceNode = data.getPath().get(0);
        PathNode destinationNode = data.getPath().get(1);

        Isl isl = islRepository.findIsl(sourceNode.getSwitchId(), sourceNode.getPortNo(),
                destinationNode.getSwitchId(), destinationNode.getPortNo());

        logger.debug("Isl relationship found: {}", isl);

        return isl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dropLink(final IslInfoData data) {
        logger.debug("Isl drop: isl={}", data);

        PathNode sourceNode = data.getPath().get(0);
        PathNode destinationNode = data.getPath().get(1);

        Switch sourceSwitch = switchRepository.findByName(sourceNode.getSwitchId());
        Switch destinationSwitch = switchRepository.findByName(destinationNode.getSwitchId());

        if (sourceSwitch == null || destinationSwitch == null) {
            logger.error("Could not find switch: source={}, destination={}", sourceSwitch, destinationSwitch);
            return;
        }

        Isl isl = islRepository.findIsl(sourceNode.getSwitchId(), sourceNode.getPortNo(),
                destinationNode.getSwitchId(), destinationNode.getPortNo());

        logger.debug("Isl relationship found: {}", isl);

        if (isl == null) {
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        //TODO: replace queries on Spring auto generated
        //islRepository.delete(isl);

        islRepository.deleteIsl(sourceNode.getSwitchId(), sourceNode.getPortNo(),
                destinationNode.getSwitchId(), destinationNode.getPortNo());

        logger.debug("Isl delete relationship: isl={}", isl);
    }
}
