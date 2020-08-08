package ke.axle.chassis.service;

import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.exceptions.GeneralBadRequest;

public interface EntityValidator {
    <T extends StandardEntity> void validateUniqueFields( T entity) throws GeneralBadRequest;
    <T extends StandardEntity> void validateRelationsFields( T entity) throws GeneralBadRequest;
}
