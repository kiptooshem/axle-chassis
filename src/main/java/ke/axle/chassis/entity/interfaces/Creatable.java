package ke.axle.chassis.entity.interfaces;

import java.util.Date;

public interface Creatable {
    Date getCreateTs();
    void setCreateTs(Date date);
    String getCreatedBy();
    void setCreatedBy(String createdBy);
}
