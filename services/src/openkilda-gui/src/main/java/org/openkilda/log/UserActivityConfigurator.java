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

package org.openkilda.log;

import org.openkilda.config.DatabaseConfigurator;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.log.dao.repository.ActivityTypeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Class UserActivityConfigurator.
 */

@Repository("userActivityConfigurator")
public class UserActivityConfigurator {

    private ActivityTypeRepository activityTypeRepository;

    /**
     * Instantiates a new user activity configurator.
     *
     * @param databaseConfigurator the database configurator
     * @param activityTypeRepository the activity type repository
     */
    public UserActivityConfigurator(
            @Autowired final DatabaseConfigurator databaseConfigurator,
            @Autowired final ActivityTypeRepository activityTypeRepository) {
        this.activityTypeRepository = activityTypeRepository;
        init();
    }

    /**
     * Inits the.
     */
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
