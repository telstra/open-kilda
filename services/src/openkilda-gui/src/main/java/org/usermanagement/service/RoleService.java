package org.usermanagement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openkilda.constants.Status;
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

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role createRole(final Role role) {
        roleValidator.validateRole(role);
        Set<PermissionEntity> permissionEntities = new HashSet<>();
        List<PermissionEntity> permissionEntityList = permissionRepository.findAll();
        for (Long permissionId : role.getPermissionId()) {
            PermissionEntity permissionEntity = permissionEntityList.parallelStream()
                    .filter((entity) -> entity.getPermissionId().equals(permissionId)).findFirst()
                    .orElse(null);

            if (!ValidatorUtil.isNull(permissionEntity)) {
                permissionEntities.add(permissionEntity);
            } else {
            	LOGGER.error("Permission with id '" + permissionId + "' not found.");
                throw new RequestValidationException(
                        messageUtil.getAttributeNotFound("permission"));
            }
        }

        RoleEntity roleEntity = RoleConversionUtil.toRoleEntity(role, permissionEntities);
        roleRepository.save(roleEntity);
        LOGGER.info("Role with name '" + roleEntity.getName() + "' created successfully.");
        return RoleConversionUtil.toRole(roleEntity, true, false);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<Role> getAllRole() {
        List<RoleEntity> roleEntityList = roleRepository.findAll();
        List<Role> roleList = RoleConversionUtil.toAllRoleResponse(roleEntityList);
        return roleList;
    }

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
				LOGGER.error("Role with role id '" + roleId + "' not found. Error: "
						+ messageUtil.getAttributeNotFound("roles"));
				throw new RequestValidationException(messageUtil.getAttributeNotFound("roles"));
			}
		}
		return roleEntities;
	}

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role getRoleById(final Long roleId) {
        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);
        if (ValidatorUtil.isNull(roleEntity)) {
        	LOGGER.error("Role with role id '" + roleId + "' not found. Error: "
					+ messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }

        return RoleConversionUtil.toRole(roleEntity, true, false);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void deleteRoleById(final Long roleId) {

        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);
        if (ValidatorUtil.isNull(roleEntity)) {
        	LOGGER.error("Role with role id '" + roleId + "' not found. Error: "
					+ messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }

        Set<UserEntity> userEntityList = userRepository.findByRoles_roleId(roleId);
        if (userEntityList.size() > 0) {
            String users = "";
            for (UserEntity userEntity : userEntityList) {
                users += !"".equals(users) ? "," + userEntity.getName() : userEntity.getName();
            }
        	LOGGER.error("Role with role id '" + roleId + "' not allowed to delete. Error: "
					+ messageUtil.getAttributeDeletionNotAllowed(roleEntity.getName(), users));
            throw new RequestValidationException(
                    messageUtil.getAttributeDeletionNotAllowed(roleEntity.getName(), users));
        }
        roleRepository.delete(roleEntity);
        LOGGER.info("Role(roleId: " + roleId + ") deleted successfully.");
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Permission getRolesByPermissionId(final Long permissionId) {

        PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);

        Set<RoleEntity> roleEntityList =
                roleRepository.findByPermissions_permissionId(permissionId);

        return RoleConversionUtil.toPermissionByRole(roleEntityList, permissionEntity);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role updateRole(final Long roleId, final Role role) {

        roleValidator.validateUpdateRole(role, roleId);

        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);

        if (ValidatorUtil.isNull(roleEntity)) {
        	LOGGER.error("Role with role id '" + roleId + "' not found. Error: "
					+ messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }

        if (role.getPermissionId() != null) {
            roleEntity.getPermissions().clear();
            for (Long permissionId : role.getPermissionId()) {
                PermissionEntity permissionEntity =
                        permissionRepository.findByPermissionId(permissionId);
                if (permissionEntity != null) {
                    roleEntity.getPermissions().add(permissionEntity);
                }
            }
        }
        roleEntity = RoleConversionUtil.toUpateRoleEntity(role, roleEntity);
        roleRepository.save(roleEntity);
        LOGGER.info("Role updated successfully (roleId: " + roleId + ")");
        return RoleConversionUtil.toRole(roleEntity, true, false);

    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Permission assignRoleByPermissionId(final Long permissionId, final Permission request) {
        PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
        
        if (ValidatorUtil.isNull(permissionEntity)) {
        	LOGGER.error("Permission with permissionId '" + permissionId + "' not found. Error: "
					+ messageUtil.getAttributeInvalid("permissionId", permissionId + ""));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("permissionId", permissionId + ""));
        }
        permissionEntity.getRoles().clear();
        if (request.getRoles() != null) {
            for (Role role : request.getRoles()) {
                RoleEntity roleEntity = roleRepository.findByRoleId(role.getRoleId());
                permissionEntity.getRoles().add(roleEntity);
            }
        }
        permissionRepository.save(permissionEntity);
        LOGGER.info("Roles assigned with permission successfully (permissionId: " + permissionId + ")");
        return RoleConversionUtil.toPermissionByRole(permissionEntity.getRoles(), permissionEntity);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<Role> getRoleByName(final Set<String> role) {
        List<Role> roles = new ArrayList<Role>();
        List<RoleEntity> roleEntities = roleRepository.findByNameIn(role);
        if (ValidatorUtil.isNull(roleEntities)) {
        	LOGGER.error("Roles with name '" + role + "' not found. Error: "
					+ messageUtil.getAttributeInvalid("role", role + ""));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("role", role + ""));
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
        	LOGGER.error("Role with role id '" + roleId + "' not found. Error: "
					+ messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }
        return RoleConversionUtil.toRole(roleEntity, false, true);
    }
}
