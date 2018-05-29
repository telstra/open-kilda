package org.usermanagement.dao.entity;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.openkilda.entity.BaseEntity;

@Entity
@Table(name = "kilda_role")
public class RoleEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = -57044334239698601L;

    @Id
    @Column(name = "role_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    @Column(name = "role", nullable = false)
    private String name;

    @Column(name = "description", nullable = true)
    private String description;

    @ManyToOne
    @JoinColumn(name = "status_id", nullable = false)
    private StatusEntity statusEntity;

    /** The roles. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permission", joinColumns = {@JoinColumn(name = "role_id")},
            inverseJoinColumns = {@JoinColumn(name = "permission_id")})
    private Set<PermissionEntity> permissions = new HashSet<PermissionEntity>();

    /**
     * Gets the role id.
     *
     * @return the role id
     */
    public Long getRoleId() {
        return roleId;
    }

    /**
     * Sets the role id.
     *
     * @param roleId the new role id
     */
    public void setRoleId(final Long roleId) {
        this.roleId = roleId;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Gets the permissions.
     *
     * @return the permissions
     */
    public Set<PermissionEntity> getPermissions() {
        return permissions;
    }

    /**
     * Sets the permissions.
     *
     * @param permissions the new permissions
     */
    public void setPermissions(final Set<PermissionEntity> permissions) {
        this.permissions = permissions;
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

    @Override
    public String toString() {
        return "RoleEntity [roleId=" + roleId + ", name=" + name + ", description=" + description + ", statusEntity="
                + statusEntity + ", permissions=" + permissions + "]";
    }

}
