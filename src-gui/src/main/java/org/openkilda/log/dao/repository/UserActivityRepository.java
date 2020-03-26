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

package org.openkilda.log.dao.repository;

import org.openkilda.log.dao.entity.UserActivityEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface UserActivityRepository extends JpaRepository<UserActivityEntity, Long> {

    public List<UserActivityEntity> findByActivityTimeGreaterThanEqualAndActivityTimeLessThanEqual(Date startTime,
            Date endTime);

    public List<UserActivityEntity> findByUserIdInAndActivityTimeGreaterThanEqualAndActivityTimeLessThanEqual(
            List<Long> userIds, Date startTime, Date endTime);

    public List<UserActivityEntity> findByActivityTimeGreaterThanEqualAndActivityTimeLessThanEqualAndActivity_IdIn(
            Date startTime, Date endTime, List<Long> id);

    public List<UserActivityEntity>
            findByUserIdInAndActivityTimeGreaterThanEqualAndActivityTimeLessThanEqualAndActivity_IdIn(
                    List<Long> userIds, Date startTime, Date endTime, List<Long> ids);

}
