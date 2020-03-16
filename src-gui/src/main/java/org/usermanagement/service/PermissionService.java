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

package org.usermanagement.service;

import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.usermanagement.conversion.PermissionConversionUtil;
import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.repository.PermissionRepository;
import org.usermanagement.dao.repository.RoleRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Permission;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;
import org.usermanagement.validator.PermissionValidator;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Class PermissionService.
 */

@Service
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class PermissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionService.class);

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionValidator permissionValidator;

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private ActivityLogger activityLogger;

    /**
     * Creates the permission.
     *
     * @param permission the permission
     * @return the permission
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Permission createPermission(final Permission permission) {
        permissionValidator.validatePermission(permission);

        PermissionEntity permissionEntity = PermissionConversionUtil.toPermissionEntity(permission);
        permissionRepository.save(permissionEntity);

        activityLogger.log(ActivityType.CREATE_PERMISSION, permission.getName());
        LOGGER.info("Permission with name '" + permission.getName() + "' created successfully.");
        return PermissionConversionUtil.toPermission(permissionEntity, null);
    }

    /**
     * Gets the all permission.
     *
     * @param loggedInUserId the logged in user id
     * @return the all permission
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<Permission> getAllPermission(final long loggedInUserId) {
        List<PermissionEntity> permissionEntities = permissionRepository.findAll();

        permissionEntities = permissionEntities.stream()
                .filter(permission -> loggedInUserId == 1 || !permission.getIsAdminPermission())
                .collect(Collectors.toList());
        List<Permission> permissions = PermissionConversionUtil.toAllPermissionResponse(permissionEntities);
        return permissions;
    }

    /**
     * Gets the permission by id.
     *
     * @param permissionId the permission id
     * @return the permission by id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Permission getPermissionById(final Long permissionId) {
        PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);

        if (ValidatorUtil.isNull(permissionEntity)) {
            LOGGER.warn("Permission with permissionId '" + permissionId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
        }
        Set<RoleEntity> roleEntityList = roleRepository.findByPermissions_permissionId(permissionId);
        return PermissionConversionUtil.toPermission(permissionEntity, roleEntityList);
    }

    /**
     * Delete permission by id.
     *
     * @param permissionId the permission id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void deletePermissionById(final Long permissionId) {

        PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
        if (ValidatorUtil.isNull(permissionEntity)) {
            throw new RequestValidationException(messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
        }
        Set<RoleEntity> roleEntityList = roleRepository.findByPermissions_permissionId(permissionId);
        if (roleEntityList.size() > 0) {
            String roles = "";
            for (RoleEntity roleEntity : roleEntityList) {
                roles += !"".equals(roles) ? "," + roleEntity.getName() : roleEntity.getName();
            }
            LOGGER.warn("Permission with permissionId '" + permissionId + "' not allowed to delete. Error: "
                    + messageUtil.getAttributeDeletionNotAllowed(permissionEntity.getName(), roles));
            throw new RequestValidationException(
                    messageUtil.getAttributeDeletionNotAllowed(permissionEntity.getName(), roles));
        }
        permissionRepository.delete(permissionEntity);
        LOGGER.info("Permission(permissionId: " + permissionId + ") deleted successfully.");
        activityLogger.log(ActivityType.DELETE_PERMISSION, permissionEntity.getName());
    }

    /**
     * Update permission.
     *
     * @param permissionId the permission id
     * @param permission the permission
     * @return the permission
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Permission updatePermission(final Long permissionId, final Permission permission) {

        permissionValidator.validateUpdatePermission(permission, permissionId);

        PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
        if (ValidatorUtil.isNull(permissionEntity)) {
            LOGGER.warn("Permission with permissionId '" + permissionId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
        }

        permissionEntity = PermissionConversionUtil.toUpatePermissionEntity(permission, permissionEntity);
        permissionRepository.save(permissionEntity);
        activityLogger.log(ActivityType.UPDATE_PERMISSION, permissionEntity.getName());
        LOGGER.info("Permission(permissionId: " + permissionId + ") updated successfully.");
        return PermissionConversionUtil.toPermission(permissionEntity, null);

    }
}
