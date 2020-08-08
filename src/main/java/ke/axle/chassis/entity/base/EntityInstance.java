package ke.axle.chassis.entity.base;

import javax.persistence.Transient;
import java.io.Serializable;

public abstract class EntityInstance implements Serializable {

    protected boolean isNew = true;

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }
}
