package org.usermanagement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
	public Role createRole(final Role roleRequest) {
		roleValidator.validateRole(roleRequest);
		Set<PermissionEntity> permissionEntities = new HashSet<>();
		List<PermissionEntity> permissionEntityList = permissionRepository.findAll();
		for (Long permissionId : roleRequest.getPermissionId()) {
			PermissionEntity permissionEntity = permissionEntityList.parallelStream()
					.filter((entity) -> entity.getPermissionId().equals(permissionId)).findFirst().orElse(null);

			if (!ValidatorUtil.isNull(permissionEntity)) {
				permissionEntities.add(permissionEntity);
			} else {
				throw new RequestValidationException(messageUtil.getAttributeNotFound("permission"));
			}
		}

		RoleEntity roleEntity = RoleConversionUtil.toRoleEntity(roleRequest, permissionEntities);
		roleRepository.save(roleEntity);
		return RoleConversionUtil.toRole(roleEntity);
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public List<Role> getAllRole() {
		List<RoleEntity> roleEntityList = roleRepository.findAll();
		List<Role> roleList = RoleConversionUtil.toAllRoleResponse(roleEntityList);
		return roleList;
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public Role getRoleById(final Long roleId) {

		RoleEntity roleEntity = roleRepository.findByroleId(roleId);
		if (ValidatorUtil.isNull(roleEntity)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
		}

		return RoleConversionUtil.toRole(roleEntity);
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public void deleteRoleById(Long roleId) {

		RoleEntity roleEntity = roleRepository.findByroleId(roleId);
		if (ValidatorUtil.isNull(roleEntity)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
		}

		List<UserEntity> userEntityList = userRepository.findByRoles_roleId(roleId);
		if (userEntityList.size() > 0) {
			String users = "";
			for (UserEntity userEntity : userEntityList) {
				users += !"".equals(users) ? "," + userEntity.getName() : userEntity.getName();
			}
			throw new RequestValidationException(
					messageUtil.getAttributeDeletionNotAllowed(roleEntity.getName(), users));
		}
		roleRepository.delete(roleEntity);
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public Permission getRolesByPermissionId(final Long permissionId) {

		PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);

		List<RoleEntity> roleEntityList = roleRepository.findByPermissions_permissionId(permissionId);

		return RoleConversionUtil.toPermissionByRole(roleEntityList, permissionEntity);
	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public Role updateRole(Long roleId, final Role role) {

		roleValidator.validateUpdateRole(role, roleId);

		RoleEntity roleEntity = roleRepository.findByroleId(roleId);

		if (ValidatorUtil.isNull(roleEntity)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
		}
		Set<PermissionEntity> permissionEntities = null;
		if (!ValidatorUtil.isNull(role.getPermissionId())) {
			permissionEntities = new HashSet<>();
			for (Long permissionId : role.getPermissionId()) {
				PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);
				permissionEntities.add(permissionEntity);
			}
		}
		roleEntity = RoleConversionUtil.toUpateRoleEntity(role, roleEntity, permissionEntities);
		roleRepository.save(roleEntity);

		return RoleConversionUtil.toRole(roleEntity);

	}

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public Permission assignRoleByPermissionId(final Long permissionId, final Permission permissionRequest) {

		PermissionEntity permissionEntity = permissionRepository.findByPermissionId(permissionId);

		for (Role role : permissionRequest.getRoles()) {
			RoleEntity roleEntity = roleRepository.findByroleId(role.getRoleId());
			Set<PermissionEntity> permissionEntities = roleEntity.getPermissions();
			permissionEntities.add(permissionEntity);

			roleRepository.save(roleEntity);
		}

		List<RoleEntity> roleEntityList = roleRepository.findByPermissions_permissionId(permissionId);
		if (ValidatorUtil.isNull(roleEntityList)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("permission_id", permissionId + ""));
		}

		return RoleConversionUtil.toPermissionByRole(roleEntityList, permissionEntity);
	}

}
