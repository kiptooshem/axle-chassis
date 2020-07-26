package ke.axle.chassis.entity.interfaces;

import java.util.Date;

public interface Updatable {
    Date getUpdateTs();
    void setUpdateTs(Date updateTs);
    String getUpdatedBy();
    void setUpdatedBy(String updatedBy);
}
