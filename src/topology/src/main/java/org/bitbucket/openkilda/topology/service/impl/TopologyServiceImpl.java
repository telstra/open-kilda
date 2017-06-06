package org.bitbucket.openkilda.topology.service.impl;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;

import org.bitbucket.openkilda.topology.domain.Isl;
import org.bitbucket.openkilda.topology.domain.Switch;
import org.bitbucket.openkilda.topology.domain.repository.IslRepository;
import org.bitbucket.openkilda.topology.domain.repository.SwitchRepository;
import org.bitbucket.openkilda.topology.domain.repository.TopologyRepository;
import org.bitbucket.openkilda.topology.model.Node;
import org.bitbucket.openkilda.topology.model.Topology;
import org.bitbucket.openkilda.topology.service.TopologyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages operations on topology.
 */
@Service
@Transactional
public class TopologyServiceImpl implements TopologyService {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(TopologyServiceImpl.class);

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
     * Topology repository.
     */
    @Autowired
    private TopologyRepository topologyRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Topology clear(String correlationId) {
        logger.debug("Clearing topology: {}={}", CORRELATION_ID, correlationId);
        topologyRepository.clear();
        return network(correlationId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Topology network(String correlationId) {
        logger.debug("Dumping topology: {}={}", CORRELATION_ID, correlationId);

        List<Isl> isls = islRepository.getAllIsl();
        logger.debug("Found isls: {}={}, {}", CORRELATION_ID, correlationId, isls);

        Iterable<Switch> switches = switchRepository.findAll();

        List<Node> nodes = new ArrayList<>();

        for (Switch sw : switches) {
            List<String> relationships = new ArrayList<>();

            for (Isl isl : isls) {
                if (isl.getSourceSwitch().equals(sw.getDpid())) {
                    relationships.add(isl.getDestinationSwitch());
                }
            }

            nodes.add(new Node(sw.getDpid(), relationships));
        }

        return new Topology(nodes);
    }
}
