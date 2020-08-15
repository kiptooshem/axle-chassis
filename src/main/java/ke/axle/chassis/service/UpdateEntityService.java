package ke.axle.chassis.service;

import ke.axle.chassis.ChasisResource;
import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.exceptions.GeneralBadRequest;

public interface UpdateEntityService {
    <T extends StandardEntity> T update(ChasisResource r, T entity) throws GeneralBadRequest;

    <T extends StandardEntity> void performMakerCheckerChecks(T currentEntity, T update) throws GeneralBadRequest;
}
