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

package org.usermanagement.conversion;

import org.openkilda.constants.Status;

import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.StatusEntity;
import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.util.ValidatorUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * The Class PermissionConversionUtil.
 */

public final class PermissionConversionUtil {
    
    private PermissionConversionUtil() {

    }

    /**
     * To all permission response.
     *
     * @param permissionList the permission list
     * @return the list
     */
    public static List<Permission> toAllPermissionResponse(final List<PermissionEntity> permissionList) {
        List<Permission> permissions = new ArrayList<>();

        for (PermissionEntity permissionEntity : permissionList) {
            permissions.add(toPermission(permissionEntity, null));
        }
        return permissions;
    }

    /**
     * To permission entity.
     *
     * @param permission the permission
     * @return the permission entity
     */
    public static PermissionEntity toPermissionEntity(final Permission permission) {
        PermissionEntity permissionEntity = new PermissionEntity();
        permissionEntity.setName(permission.getName());
        permissionEntity.setDescription(permission.getDescription());
        permissionEntity.setIsEditable(permission.getIsEditable());
        permissionEntity.setIsAdminPermission(permission.getIsAdminPermission());
        StatusEntity statusEntity = Status.ACTIVE.getStatusEntity();
        permissionEntity.setStatusEntity(statusEntity);
        return permissionEntity;
    }

    /**
     * To permission.
     *
     * @param permissionEntity the permission entity
     * @param roleEntities the role entities
     * @return the permission
     */
    public static Permission toPermission(final PermissionEntity permissionEntity, final Set<RoleEntity> roleEntities) {
        Permission permission = new Permission();
        permission.setName(permissionEntity.getName());
        permission.setPermissionId(permissionEntity.getPermissionId());
        permission.setIsEditable(permissionEntity.getIsEditable());
        permission.setIsAdminPermission(permissionEntity.getIsAdminPermission());
        permission.setStatus(permissionEntity.getStatusEntity().getStatus());
        permission.setDescription(permissionEntity.getDescription());

        if (!ValidatorUtil.isNull(roleEntities)) {
            List<Role> roles = new ArrayList<>();
            for (RoleEntity entity : roleEntities) {
                Role role = new Role();
                role.setRoleId(entity.getRoleId());
                role.setName(entity.getName());
                roles.add(role);
            }
            permission.setRoles(roles);
        }
        return permission;
    }

    /**
     * To upate permission entity.
     *
     * @param permission the permission
     * @param permissionEntity the permission entity
     * @return the permission entity
     */
    public static PermissionEntity toUpatePermissionEntity(final Permission permission,
            final PermissionEntity permissionEntity) {
        if (!ValidatorUtil.isNull(permission.getStatus())) {
            StatusEntity newStatusEntity = Status.getStatusByName(permission.getStatus()).getStatusEntity();
            permissionEntity.setStatusEntity(newStatusEntity);
        }

        if (!ValidatorUtil.isNull(permission.getName())) {
            permissionEntity.setName(permission.getName());
        }

        if (!ValidatorUtil.isNull(permission.getDescription())) {
            permissionEntity.setDescription(permission.getDescription());
        }
        if (!ValidatorUtil.isNull(permission.getIsAdminPermission())) {
            permissionEntity.setIsAdminPermission(permission.getIsAdminPermission());
        }
        permissionEntity.setCreatedDate(new Date());
        permissionEntity.setUpdatedDate(new Date());
        return permissionEntity;
    }
}
