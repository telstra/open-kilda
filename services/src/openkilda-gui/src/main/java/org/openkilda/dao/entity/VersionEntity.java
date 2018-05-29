package org.openkilda.dao.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "version")
public class VersionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "version_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long versionId;

    @Column(name = "version_number", nullable = false)
    private Long versionNumber;

    /** The created date. */
    @Column(name = "version_deployment_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date deploymentDate;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(final Long versionId) {
        this.versionId = versionId;
    }

    public Long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(final Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Date getDeploymentDate() {
        return deploymentDate;
    }

    public void setDeploymentDate(final Date deploymentDate) {
        this.deploymentDate = deploymentDate;
    }
}
