package ke.axle.chassis.entity.base;

import ke.axle.chassis.entity.interfaces.Creatable;
import ke.axle.chassis.entity.interfaces.SoftDelete;
import ke.axle.chassis.entity.interfaces.Updatable;
import ke.axle.chassis.entity.interfaces.Versioned;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.util.Date;

@MappedSuperclass
public class StandardNumberEntity extends BaseNumberEntity implements Versioned, Creatable, Updatable, SoftDelete {
    @Column(name = "VERSION")
    protected Integer version;

    @Column(name = "CREATE_TS")
    protected Date createTs;

    @Column(name = "CREATED_BY")
    protected String createdBy;

    @Column(name = "UPDATE_TS")
    protected Date updateTs;

    @Column(name = "UPDATED_BY")
    protected String updatedBy;

    @Column(name = "DELETE_TS")
    protected Date deleteTs;

    @Column(name = "DELETED_BY")
    protected String deletedBy;

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
}
