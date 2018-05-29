package org.usermanagement.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.usermanagement.dao.entity.PermissionEntity;
import org.usermanagement.dao.entity.RoleEntity;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    public RoleEntity findByroleId(Long roleId);
    
    public List<RoleEntity> findByPermissions(PermissionEntity permissionEntity);
    
    List<RoleEntity> findByPermissions_permissionId(Long permissionId);
}
