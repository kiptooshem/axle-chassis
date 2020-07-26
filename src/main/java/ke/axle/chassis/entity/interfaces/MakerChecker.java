package ke.axle.chassis.entity.interfaces;

import ke.axle.chassis.entity.enums.MakerCheckerAction;
import ke.axle.chassis.entity.enums.MakerCheckerActionStatus;

public interface MakerChecker {
    void setAction(MakerCheckerAction action);
    MakerCheckerAction getAction();
    void setActionStatus(MakerCheckerActionStatus status);
    MakerCheckerActionStatus getActionStatus();
}
