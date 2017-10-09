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
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.topology.domain.Switch;
import org.openkilda.topology.domain.SwitchStateType;
import org.openkilda.topology.domain.repository.SwitchRepository;
import org.openkilda.topology.service.SwitchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages operations on switches.
 */
@Service
@Transactional
public class SwitchServiceImpl implements SwitchService {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(SwitchServiceImpl.class);

    /**
     * Inventory repository.
     */
    @Autowired
    private SwitchRepository switchRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Switch get(final String switchId) {
        return switchRepository.findByName(switchId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Switch> dump() {
        return switchRepository.findAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Switch add(final SwitchInfoData data) {
        String name = data.getSwitchId();
        String state = SwitchStateType.INACTIVE.toString().toLowerCase();
        logger.debug("Switch adding: switch-id={}", name);

        Switch sw = switchRepository.findByName(name);
        if (sw != null) {
            throw new MessageException(ErrorType.ALREADY_EXISTS, System.currentTimeMillis());
        }

        sw = new Switch(name, state, data.getAddress(), data.getHostname(), data.getDescription());
        sw.setLabels(state, data.getDescription());
        switchRepository.save(sw);

        return sw;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Switch remove(final SwitchInfoData data) {
        String name = data.getSwitchId();
        logger.debug("Switch removing: switch-id={}", name);

        Switch sw = switchRepository.findByName(name);
        if (sw == null) {
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        switchRepository.delete(sw);

        return sw;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Switch activate(final SwitchInfoData data) {
        String name = data.getSwitchId();
        String state = SwitchStateType.ACTIVE.toString().toLowerCase();
        logger.debug("Switch activating: switch-id={}", name);

        Switch sw = switchRepository.findByName(name);
        if (sw == null) {
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        sw.setState(state);
        sw.setLabels(state, data.getDescription());
        switchRepository.save(sw);

        return sw;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Switch deactivate(final SwitchInfoData data) {
        String name = data.getSwitchId();
        String state = SwitchStateType.INACTIVE.toString().toLowerCase();
        logger.debug("Switch deactivating: switch-id={}", name);

        Switch sw = switchRepository.findByName(name);
        if (sw == null) {
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        sw.setState(state);
        sw.setLabels(state, data.getDescription());
        switchRepository.save(sw);

        return sw;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Switch change(final SwitchInfoData data) {
        String name = data.getSwitchId();
        logger.debug("Switch changing: switch-id={}", name);

        throw new MessageException(ErrorType.NOT_IMPLEMENTED, System.currentTimeMillis());
    }
}
