package org.usermanagement.dao.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.openkilda.entity.BaseEntity;

@Entity
@Table(name = "kilda_permission")
public class PermissionEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "permission_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long permissionId;

    @Column(name = "permission", nullable = false)
    private String name;

    @Column(name = "description", nullable = true)
    private String description;

    @Column(name = "is_editable", nullable = false)
    private Boolean isEditable;

    @Column(name = "is_admin_permission", nullable = false)
    private Boolean isAdminPermission;

    @ManyToOne
    @JoinColumn(name = "status_id", nullable = false)
    private StatusEntity statusEntity;

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(final Long permissionId) {
        this.permissionId = permissionId;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }


    public StatusEntity getStatusEntity() {
        return statusEntity;
    }

    public void setStatusEntity(final StatusEntity statusEntity) {
        this.statusEntity = statusEntity;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public Boolean getIsEditable() {
        return isEditable;
    }

    public void setIsEditable(final Boolean isEditable) {
        this.isEditable = isEditable;
    }

    public Boolean getIsAdminPermission() {
        return isAdminPermission;
    }

    public void setIsAdminPermission(final Boolean isAdminPermission) {
        this.isAdminPermission = isAdminPermission;
    }

    @Override
    public String toString() {
        return "PermissionEntity [permissionId=" + permissionId + ", name=" + name + ", description=" + description
                + ", statusEntity=" + statusEntity + "]";
    }
}
