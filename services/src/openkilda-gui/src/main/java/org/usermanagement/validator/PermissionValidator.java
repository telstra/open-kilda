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

import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.repository.PermissionRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Permission;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

/**
 * The Class PermissionValidator.
 */

@Component
public class PermissionValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionValidator.class);

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private MessageUtils messageUtil;

    /**
     * Validate permission.
     *
     * @param permission the permission
     */
    public void validatePermission(final Permission permission) {

        if (ValidatorUtil.isNull(permission.getName())) {
            LOGGER.warn("Validation fail for permission(name: " + permission.getName() + "). Error: "
                    + messageUtil.getAttributeNotNull("name"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        }

        PermissionEntity permissionEntity = permissionRepository.findByName(permission.getName());
        if (permissionEntity != null) {
            LOGGER.warn("Validation fail for permission(name: " + permission.getName() + "). Error: "
                    + messageUtil.getAttributeUnique("name"));
            throw new RequestValidationException(messageUtil.getAttributeUnique("name"));
        }
    }

    /**
     * Validate update permission.
     *
     * @param permission the permission
     * @param permissionId the permission id
     */
    public void validateUpdatePermission(final Permission permission, final Long permissionId) {

        if (ValidatorUtil.isNull(permissionId)) {
            LOGGER.warn("Validation fail for permission(permission_id: " + permissionId + "). Error: "
                    + messageUtil.getAttributeNotNull("permission_id"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("permission_id"));
        } else if (ValidatorUtil.isNull(permission.getName()) && ValidatorUtil.isNull(permission.getStatus())
                && ValidatorUtil.isNull(permission.getDescription())) {
            LOGGER.warn("Validation fail for role(name,description and status: " + permission.getName() + ","
                    + permission.getDescription() + "," + permission.getStatus() + "). Error: "
                    + messageUtil.getAttributeNotNull("name,description and status"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name,description and status"));
        }

        if (!ValidatorUtil.isNull(permission.getName())) {
            PermissionEntity permissionEntity = permissionRepository.findByName(permission.getName());
            if (permissionEntity != null && !permissionEntity.getPermissionId().equals(permissionId)) {
                LOGGER.warn("Validation fail for permission(name: " + permission.getName() + "). Error: "
                        + messageUtil.getAttributeUnique("name"));
                throw new RequestValidationException(messageUtil.getAttributeUnique("name"));
            }
        }
    }
}
