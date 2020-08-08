package ke.axle.chassis.entity.base;


import ke.axle.chassis.entity.enums.MakerCheckerAction;
import ke.axle.chassis.entity.enums.MakerCheckerActionStatus;
import ke.axle.chassis.entity.interfaces.*;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.MappedSuperclass;
import java.util.Date;

@MappedSuperclass
public class StandardEntity extends BaseUuidEntity implements Versioned, Creatable, Updatable, SoftDelete, MakerChecker {

    @Column(name = "VERSION")
    protected Integer version = 1;

    @Column(name = "CREATE_TS")
    protected Date createTs = new Date();

    @Column(name = "CREATED_BY")
    protected String createdBy;

    @Column(name = "UPDATE_TS")
    protected Date updateTs = new Date();

    @Column(name = "UPDATED_BY")
    protected String updatedBy;

    @Column(name = "DELETE_TS")
    protected Date deleteTs;

    @Column(name = "DELETED_BY")
    protected String deletedBy;

    @Column(name = "ACTION")
    @Enumerated(EnumType.STRING)
    protected MakerCheckerAction action = MakerCheckerAction.CREATE;

    @Column(name = "ACTION_STATUS")
    @Enumerated(EnumType.STRING)
    protected MakerCheckerActionStatus actionStatus = MakerCheckerActionStatus.UNAPPROVED;

    @Override
    public Date getCreateTs() {
        return this.createTs;
    }

    @Override
    public void setCreateTs(Date date) {
        this.createTs = date;
    }

    @Override
    public String getCreatedBy() {
        return this.createdBy;
    }

    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public Boolean isDeleted() {
        return this.deleteTs != null;
    }

    @Override
    public Date getDeleteTs() {
        return this.deleteTs;
    }

    @Override
    public String getDeletedBy() {
        return this.deletedBy;
    }

    @Override
    public void setDeleteTs(Date deleteTs) {
        this.deleteTs = deleteTs;
    }

    @Override
    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    @Override
    public Date getUpdateTs() {
        return this.updateTs;
    }

    @Override
    public void setUpdateTs(Date updateTs) {
        this.updateTs = updateTs;
    }

    @Override
    public String getUpdatedBy() {
        return this.updatedBy;
    }

    @Override
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public Integer getVersion() {
        return this.version;
    }

    @Override
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public void setAction(MakerCheckerAction action) {
        this.action = action;
    }

    @Override
    public MakerCheckerAction getAction() {
        return this.action;
    }

    @Override
    public void setActionStatus(MakerCheckerActionStatus status) {
        this.actionStatus = status;
    }

    @Override
    public MakerCheckerActionStatus getActionStatus() {
        return this.actionStatus;
    }
}
