package org.openkilda.log;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.openkilda.config.DatabaseConfigurator;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.log.dao.repository.ActivityTypeRepository;

@Repository("userActivityConfigurator")
public class UserActivityConfigurator {

    private ActivityTypeRepository activityTypeRepository;

    public UserActivityConfigurator(@SuppressWarnings("unused") @Autowired final DatabaseConfigurator databaseConfigurator,
            @Autowired final ActivityTypeRepository activityTypeRepository) {
        this.activityTypeRepository = activityTypeRepository;
        init();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void init() {
        activityTypeRepository.findAll().stream().forEach((entity) -> {
            for (ActivityType activity : ActivityType.values()) {
                if (activity.getId().equals(entity.getId())) {
                    activity.setActivityTypeEntity(entity);
                }
            }
        });
    }
}