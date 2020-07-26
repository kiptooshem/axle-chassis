package ke.axle.chassis.entity.base;

import java.io.Serializable;

public interface Entity<T> extends Serializable {
    T getId();
}
