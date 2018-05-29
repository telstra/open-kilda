package org.usermanagement.validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.repository.RoleRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Role;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

@Component
public class RoleValidator {

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private RoleRepository roleRepository;

    public void validateRole(final Role role) {

        if (ValidatorUtil.isNull(role.getName())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        }

        List<RoleEntity> roleEntityList = roleRepository.findAll();
        if (roleEntityList.parallelStream()
                .anyMatch((roleEntity) -> roleEntity.getName().equalsIgnoreCase(role.getName()))) {
            throw new RequestValidationException(messageUtil.getAttributeUnique("name"));
        }
    }

    public void validateUpdateRole(final Role role, Long roleId) {

        if (ValidatorUtil.isNull(roleId)) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("role_id"));
        } else if (ValidatorUtil.isNull(role.getName()) && ValidatorUtil.isNull(role.getStatus())
                && ValidatorUtil.isNull(role.getPermissionId()) && ValidatorUtil.isNull(role.getDescription())) {
            throw new RequestValidationException(
                    messageUtil.getAttributeNotNull("name, status, description and permissions"));
        }

        if (!ValidatorUtil.isNull(role.getName())) {
            RoleEntity roleEntity = roleRepository.findByroleId(roleId);
            if (!roleEntity.getName().equalsIgnoreCase(role.getName())) {
                validateRole(role);
            }
        }
    }
}
