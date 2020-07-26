package ke.axle.chassis.entity.interfaces;

import java.util.Date;

public interface SoftDelete {
    Boolean isDeleted();
    Date getDeleteTs();
    String getDeletedBy();
    void setDeleteTs(Date deleteTs);
    void setDeletedBy(String deletedBy);
}
