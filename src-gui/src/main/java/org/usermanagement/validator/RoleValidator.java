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

package org.usermanagement.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.repository.RoleRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Role;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

import java.util.List;

/**
 * The Class RoleValidator.
 */

@Component
public class RoleValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleValidator.class);

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private RoleRepository roleRepository;

    /**
     * Validate role.
     *
     * @param role the role
     */
    public void validateRole(final Role role) {

        if (ValidatorUtil.isNull(role.getName())) {
            LOGGER.warn("Validation fail for role(name: " + role.getName() + "). Error: "
                    + messageUtil.getAttributeNotNull("name"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        }

        List<RoleEntity> roleEntityList = roleRepository.findAll();
        if (roleEntityList.parallelStream()
                .anyMatch((roleEntity) -> roleEntity.getName().equalsIgnoreCase(role.getName()))) {
            LOGGER.warn("Validation fail for role(name: " + role.getName() + "). Error: "
                    + messageUtil.getAttributeUnique("name"));
            throw new RequestValidationException(messageUtil.getAttributeUnique("name"));
        }
    }

    /**
     * Validate update role.
     *
     * @param role the role
     * @param roleId the role id
     */
    public void validateUpdateRole(final Role role, Long roleId) {

        if (ValidatorUtil.isNull(roleId)) {
            LOGGER.warn("Validation fail for role(role_id: " + roleId + "). Error: "
                    + messageUtil.getAttributeNotNull("role_id"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("role_id"));
        } else if (ValidatorUtil.isNull(role.getName()) && ValidatorUtil.isNull(role.getStatus())
                && ValidatorUtil.isNull(role.getPermissionId()) && ValidatorUtil.isNull(role.getDescription())) {
            LOGGER.warn("Validation fail for role(name, status, description and permissions: " + role.getName() + ","
                    + role.getStatus() + "," + role.getDescription() + "," + role.getPermissions() + "). Error: "
                    + messageUtil.getAttributeNotNull("name, status, description and permissions"));
            throw new RequestValidationException(
                    messageUtil.getAttributeNotNull("name, status, description and permissions"));
        }

        if (!ValidatorUtil.isNull(role.getName())) {
            RoleEntity roleEntity = roleRepository.findByRoleId(roleId);
            if (!roleEntity.getName().equalsIgnoreCase(role.getName())) {
                validateRole(role);
            }
        }
    }
}
