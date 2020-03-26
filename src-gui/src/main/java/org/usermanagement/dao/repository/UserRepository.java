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

package org.usermanagement.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.usermanagement.dao.entity.UserEntity;

import java.util.List;
import java.util.Set;

/**
 * The Interface UserRepository.
 */

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Find by username.
     *
     * @param userName the user name
     * @return the user entity
     */
    UserEntity findByUsernameIgnoreCase(String userName);

    /**
     * Find by active flag.
     *
     * @param activeFlag the active flag
     * @return the list
     */
    List<UserEntity> findByActiveFlag(Boolean activeFlag);

    /**
     * Find byUserId.
     * 
     * @param userId the user id
     * @return UserEntity
     */
    UserEntity findByUserId(Long userId);

    /**
     * Find by roles role id.
     *
     * @param roleId the role id
     * @return the sets the
     */
    Set<UserEntity> findByRoles_roleId(Long roleId);
    
    /**
     * Find by user id in.
     *
     * @param userIds the user ids
     * @return the list
     */
    List<UserEntity> findByUserIdIn(Set<Long> userIds);
}
