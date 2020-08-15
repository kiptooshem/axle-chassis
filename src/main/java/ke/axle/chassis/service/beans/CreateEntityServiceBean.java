package ke.axle.chassis.service.beans;

import ke.axle.chassis.ChasisResource;
import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.entity.enums.MakerCheckerAction;
import ke.axle.chassis.entity.enums.MakerCheckerActionStatus;
import ke.axle.chassis.exceptions.GeneralBadRequest;
import ke.axle.chassis.service.CreateEntityService;
import ke.axle.chassis.service.EntityFieldManager;
import ke.axle.chassis.service.EntityValidator;
import ke.axle.chassis.service.listener.CreateEntityListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;

@Component
public class CreateEntityServiceBean implements CreateEntityService {

    private EntityManager entityManager;
    private EntityValidator entityValidator;

    @Autowired
    public CreateEntityServiceBean(EntityManager entityManager,
                                   EntityValidator entityValidator){
        this.entityManager = entityManager;
        this.entityValidator = entityValidator;
    }

    @Override
    public <T extends StandardEntity> T create(ChasisResource r, T entity) throws GeneralBadRequest {

        CreateEntityListener<T> createEntityListener = null;
        if(r instanceof CreateEntityListener){
             createEntityListener = (CreateEntityListener<T>) r;

            createEntityListener.beforeCreateEntity(entity, this.entityManager);
        }

        this.entityValidator.validateUniqueFields(entity);

        this.entityValidator.validateRelationsFields(entity);

        entity.setAction(MakerCheckerAction.CREATE);

        entity.setActionStatus(MakerCheckerActionStatus.UNAPPROVED);

        entityManager.persist(entity);

        if(createEntityListener != null){
            createEntityListener.afterCreateEntity(entity, entityManager);
        }

        return entity;
    }
}
