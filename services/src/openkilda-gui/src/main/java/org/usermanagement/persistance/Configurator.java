package org.usermanagement.persistance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.openkilda.config.DatabaseConfigurator;
import org.openkilda.constants.Status;
import org.usermanagement.dao.repository.StatusRepository;

@Repository("configurator")
public class Configurator {

    private StatusRepository statusRepository;


    /**
     * Instantiates a new configurator.
     *
     * @param statusRepository the status repository.
     */
    public Configurator(@SuppressWarnings("unused") @Autowired final DatabaseConfigurator databaseConfigurator,
            @Autowired final StatusRepository statusRepository) {
        this.statusRepository = statusRepository;
        init();
    }

    /**
     * Inits the status.
     */
    public void init() {
        loadStatus();
    }

    /**
     * Load status.
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void loadStatus() {
        statusRepository.findAll().stream().forEach((entity) -> {
            for (Status status : Status.values()) {
                if (status.getCode().equalsIgnoreCase(entity.getStatusCode())) {
                    status.setStatusEntity(entity);
                }
            }
        });
    }


}
