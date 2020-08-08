package ke.axle.chassis.service;

import ke.axle.chassis.ChasisResource;
import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.exceptions.GeneralBadRequest;

public interface CreateEntityService {
    <T extends StandardEntity> T create(ChasisResource r, T entity) throws GeneralBadRequest;
}
