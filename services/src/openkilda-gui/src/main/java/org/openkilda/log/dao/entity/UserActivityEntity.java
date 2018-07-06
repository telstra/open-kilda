package org.openkilda.log.dao.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "user_activity")
public class UserActivityEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "user_activity_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_number", nullable = false)
    private Long userId;

    @ManyToOne
    @JoinColumn(name = "actvity_id", nullable = false)
    private ActivityTypeEntity activity;

    @Column(name = "object_id", nullable = true)
    private String objectId;

    @Column(name = "client_ip", nullable = true)
    private String clientIp;

    @Column(name = "activity_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date activityTime;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public ActivityTypeEntity getActivity() {
        return activity;
    }

    public void setActivity(final ActivityTypeEntity activity) {
        this.activity = activity;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(final String objectId) {
        this.objectId = objectId;
    }

    public Date getActivityTime() {
        return activityTime;
    }

    public void setActivityTime(final Date activityTime) {
        this.activityTime = activityTime;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(final String clientIp) {
        this.clientIp = clientIp;
    }
}
