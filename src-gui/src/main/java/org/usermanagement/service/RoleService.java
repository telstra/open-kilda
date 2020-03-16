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

import org.openkilda.constants.Status;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.usermanagement.conversion.RoleConversionUtil;
import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.PermissionRepository;
import org.usermanagement.dao.repository.RoleRepository;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;
import org.usermanagement.validator.RoleValidator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Class RoleService.
 */

@Service
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class RoleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleService.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private RoleValidator roleValidator;

    @Autowired
    private ActivityLogger activityLogger;

    /**
     * Creates the role.
     *
     * @param role the role
     * @return the role
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role createRole(final Role role) {
        roleValidator.validateRole(role);
        Set<PermissionEntity> permissionEntities = new HashSet<>();
        List<PermissionEntity> permissionEntityList = permissionRepository.findAll();
        for (Long permissionId : role.getPermissionId()) {
            PermissionEntity permissionEntity = permissionEntityList.parallelStream()
                    .filter((entity) -> entity.getPermissionId().equals(permissionId)).findFirst().orElse(null);

            if (!ValidatorUtil.isNull(permissionEntity)) {
                permissionEntities.add(permissionEntity);
            } else {
                LOGGER.warn("Permission with id '" + permissionId + "' not found.");
                throw new RequestValidationException(messageUtil.getAttributeNotFound("permission"));
            }
        }

        RoleEntity roleEntity = RoleConversionUtil.toRoleEntity(role, permissionEntities);
        roleRepository.save(roleEntity);
        activityLogger.log(ActivityType.CREATE_ROLE, role.getName());
        LOGGER.info("Role with name '" + roleEntity.getName() + "' created successfully.");
        return RoleConversionUtil.toRole(roleEntity, true, false);
    }

    /**
     * Gets the all role.
     *
     * @return the all role
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<Role> getAllRole() {
        List<RoleEntity> roleEntityList = roleRepository.findAll();
        List<Role> roleList = RoleConversionUtil.toAllRoleResponse(roleEntityList);
        return roleList;
    }

    /**
     * Gets the roles by id.
     *
     * @param roleIds the role ids
     * @return the roles by id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Set<RoleEntity> getRolesById(final List<Long> roleIds) {
        Set<RoleEntity> roleEntities = new HashSet<>();
        List<RoleEntity> roleEntityList = roleRepository.findAll();
        for (Long roleId : roleIds) {
            RoleEntity roleEntity = roleEntityList.parallelStream()
                    .filter((entity) -> entity.getRoleId().equals(roleId)).findFirst().orElse(null);

            if (!ValidatorUtil.isNull(roleEntity)) {
                roleEntities.add(roleEntity);
            } else {
                LOGGER.warn("Role with role id '" + roleId + "' not found. Error: "
                        + messageUtil.getAttributeNotFound("roles"));
                throw new RequestValidationException(messageUtil.getAttributeNotFound("roles"));
            }
        }
        return roleEntities;
    }

    /**
     * Gets the role by id.
     *
     * @param roleId the role id
     * @return the role by id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role getRoleById(final Long roleId) {
        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);
        if (ValidatorUtil.isNull(roleEntity)) {
            LOGGER.warn("Role with role id '" + roleId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }

        return RoleConversionUtil.toRole(roleEntity, true, false);
    }

    /**
     * Delete role by id.
     *
     * @param roleId the role id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void deleteRoleById(final Long roleId) {

        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);
        if (ValidatorUtil.isNull(roleEntity)) {
            LOGGER.warn("Role with role id '" + roleId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }

        Set<UserEntity> userEntityList = userRepository.findByRoles_roleId(roleId);
        if (userEntityList.size() > 0) {
            String users = "";
            for (UserEntity userEntity : userEntityList) {
                users += !"".equals(users) ? "," + userEntity.getName() : userEntity.getName();
            }
            LOGGER.warn("Role with role id '" + roleId + "' not allowed to delete. Error: "
                    + messageUtil.getAttributeDeletionNotAllowed(roleEntity.getName(), users));
            throw new RequestValidationException(
                    messageUtil.getAttributeDeletionNotAllowed(roleEntity.getName(), users));
        }
        roleRepository.delete(roleEntity);
        activityLogger.log(ActivityType.DELETE_ROLE, roleEntity.getName());
        LOGGER.info("Role(roleId: " + roleId + ") deleted successfully.");
    }

    /**
     * Gets the roles by permission id.
     *
     * @param permissionId the permission id
     * @return the roles by permission id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Permission getRolesByPermissionId(final Long permissionId) {

        PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);

        Set<RoleEntity> roleEntityList = roleRepository.findByPermissions_permissionId(permissionId);

        return RoleConversionUtil.toPermissionByRole(roleEntityList, permissionEntity);
    }

    /**
     * Update role.
     *
     * @param roleId the role id
     * @param role the role
     * @return the role
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role updateRole(final Long roleId, final Role role) {

        roleValidator.validateUpdateRole(role, roleId);

        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);

        if (ValidatorUtil.isNull(roleEntity)) {
            LOGGER.warn("Role with role id '" + roleId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }

        if (role.getPermissionId() != null) {
            roleEntity.getPermissions().clear();
            for (Long permissionId : role.getPermissionId()) {
                PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
                if (permissionEntity != null) {
                    roleEntity.getPermissions().add(permissionEntity);
                }
            }
        }
        roleEntity = RoleConversionUtil.toUpateRoleEntity(role, roleEntity);
        roleRepository.save(roleEntity);
        activityLogger.log(ActivityType.UPDATE_ROLE, roleEntity.getName());
        LOGGER.info("Role updated successfully (roleId: " + roleId + ")");
        return RoleConversionUtil.toRole(roleEntity, true, false);

    }

    /**
     * Assign role by permission id.
     *
     * @param permissionId the permission id
     * @param request the request
     * @return the permission
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Permission assignRoleByPermissionId(final Long permissionId, final Permission request) {
        PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);

        if (ValidatorUtil.isNull(permissionEntity)) {
            LOGGER.warn("Permission with permissionId '" + permissionId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("permissionId", permissionId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("permissionId", permissionId + ""));
        }
        permissionEntity.getRoles().clear();
        if (request.getRoles() != null) {
            for (Role role : request.getRoles()) {
                RoleEntity roleEntity = roleRepository.findByRoleId(role.getRoleId());
                permissionEntity.getRoles().add(roleEntity);
            }
        }
        permissionRepository.save(permissionEntity);
        activityLogger.log(ActivityType.ASSIGN_ROLES_TO_PERMISSION, permissionEntity.getName());
        LOGGER.info("Roles assigned with permission successfully (permissionId: " + permissionId + ")");
        return RoleConversionUtil.toPermissionByRole(permissionEntity.getRoles(), permissionEntity);
    }

    /**
     * Gets the role by name.
     *
     * @param role the role
     * @return the role by name
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<Role> getRoleByName(final Set<String> role) {
        List<Role> roles = new ArrayList<Role>();
        List<RoleEntity> roleEntities = roleRepository.findByNameIn(role);
        if (ValidatorUtil.isNull(roleEntities)) {
            LOGGER.warn("Roles with name '" + role + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("role", role + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("role", role + ""));
        }
        for (RoleEntity roleEntity : roleEntities) {
            if (Status.ACTIVE.getStatusEntity().getStatus()
                    .equalsIgnoreCase(roleEntity.getStatusEntity().getStatus())) {
                roles.add(RoleConversionUtil.toRole(roleEntity, true, false));
            }
        }
        return roles;
    }

    /**
     * Gets the user by role id.
     *
     * @param roleId the role id
     * @return the user by role id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role getUserByRoleId(final Long roleId) {
        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);
        if (ValidatorUtil.isNull(roleEntity)) {
            LOGGER.warn("Role with role id '" + roleId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }
        return RoleConversionUtil.toRole(roleEntity, false, true);
    }
}
