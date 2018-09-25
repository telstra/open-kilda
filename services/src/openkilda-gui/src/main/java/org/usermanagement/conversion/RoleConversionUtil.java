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
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.model.Permission;
import org.usermanagement.model.Role;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.ValidatorUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * The Class RoleConversionUtil.
 */

public final class RoleConversionUtil {
    
    private RoleConversionUtil() {
        
    }

    /**
     * To role entity.
     *
     * @param role the role
     * @param permissionEntitySet the permission entity set
     * @return the role entity
     */
    public static RoleEntity toRoleEntity(final Role role, final Set<PermissionEntity> permissionEntitySet) {

        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setName(role.getName());
        roleEntity.setPermissions(permissionEntitySet);
        roleEntity.setDescription(role.getDescription());

        StatusEntity statusEntity = Status.ACTIVE.getStatusEntity();
        roleEntity.setStatusEntity(statusEntity);
        return roleEntity;
    }

    /**
     * To role.
     *
     * @param roleEntity the role entity
     * @param withPermissions the with permissions
     * @param withUsers the with users
     * @return the role
     */
    public static Role toRole(final RoleEntity roleEntity, final boolean withPermissions, final boolean withUsers) {
        Role role = new Role();
        role.setName(roleEntity.getName());
        role.setRoleId(roleEntity.getRoleId());
        role.setStatus(roleEntity.getStatusEntity().getStatus());
        role.setDescription(roleEntity.getDescription());

        if (withPermissions) {
            List<Permission> permissionList = new ArrayList<Permission>();

            if (!ValidatorUtil.isNull(roleEntity.getPermissions())) {
                for (PermissionEntity permissionEntity : roleEntity.getPermissions()) {
                    permissionList.add(PermissionConversionUtil.toPermission(permissionEntity, null));
                }
                role.setPermissions(permissionList);
            }
        }

        if (withUsers) {
            List<UserInfo> userInfoList = new ArrayList<>();
            for (UserEntity userEntity : roleEntity.getUsers()) {
                if (userEntity.getUserId() != 1) {
                    UserInfo userInfo = new UserInfo();
                    userInfo.setUserId(userEntity.getUserId());
                    userInfo.setName(userEntity.getName());
                    userInfoList.add(userInfo);
                }
            }
            role.setUserInfo(userInfoList);
        }
        return role;
    }

    /**
     * To all role response.
     *
     * @param roleEntityList the role entity list
     * @return the list
     */
    public static List<Role> toAllRoleResponse(final List<RoleEntity> roleEntityList) {
        List<Role> roleList = new ArrayList<>();

        for (RoleEntity roleEntity : roleEntityList) {
            roleList.add(toRole(roleEntity, true, false));
        }
        return roleList;
    }

    /**
     * To permission by role.
     *
     * @param roleEntityList the role entity list
     * @param permissionEntity the permission entity
     * @return the permission
     */
    public static Permission toPermissionByRole(final Set<RoleEntity> roleEntityList,
            final PermissionEntity permissionEntity) {
        Permission permission = new Permission();
        permission.setName(permissionEntity.getName());
        permission.setDescription(permissionEntity.getDescription());
        permission.setPermissionId(permissionEntity.getPermissionId());

        List<Role> role = new ArrayList<>();
        for (RoleEntity roleEntity : roleEntityList) {
            Role roles = new Role();
            roles.setRoleId(roleEntity.getRoleId());
            roles.setName(roleEntity.getName());
            role.add(roles);
        }

        permission.setRoles(role);
        return permission;
    }

    /**
     * To upate role entity.
     *
     * @param role the role
     * @param roleEntity the role entity
     * @return the role entity
     */
    public static RoleEntity toUpateRoleEntity(final Role role, final RoleEntity roleEntity) {
        if (!ValidatorUtil.isNull(role.getStatus())) {
            StatusEntity newStatusEntity = Status.getStatusByName(role.getStatus()).getStatusEntity();
            roleEntity.setStatusEntity(newStatusEntity);
        }

        if (!ValidatorUtil.isNull(role.getName())) {
            roleEntity.setName(role.getName());
        }

        if (!ValidatorUtil.isNull(role.getDescription())) {
            roleEntity.setDescription(role.getDescription());
        }

        roleEntity.setCreatedDate(new Date());
        roleEntity.setUpdatedDate(new Date());
        return roleEntity;
    }
}
