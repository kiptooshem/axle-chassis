package ke.axle.chassis.service.beans;

import ke.axle.chassis.ChasisResource;
import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.entity.enums.MakerCheckerAction;
import ke.axle.chassis.entity.enums.MakerCheckerActionStatus;
import ke.axle.chassis.exceptions.GeneralBadRequest;
import ke.axle.chassis.service.DataManager;
import ke.axle.chassis.service.EntityValidator;
import ke.axle.chassis.service.UpdateEntityService;
import ke.axle.chassis.service.listener.UpdateEntityListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import java.util.Date;

@Component
public class UpdateEntityServiceBean implements UpdateEntityService {

    private EntityManager entityManager;
    private EntityValidator entityValidator;
    private DataManager dataManager;
    @Autowired
    public UpdateEntityServiceBean(EntityManager entityManager, EntityValidator entityValidator,
                                   DataManager dataManager){
        this.entityManager = entityManager;
        this.entityValidator = entityValidator;
        this.dataManager = dataManager;
    }

    @Override
    public <T extends StandardEntity> T update(ChasisResource r, T entity) throws GeneralBadRequest {

        UpdateEntityListener<T> tUpdateEntityListener = null;

        if(r instanceof UpdateEntityListener){
            tUpdateEntityListener = (UpdateEntityListener) r;

            tUpdateEntityListener.beforeUpdateEntity(entity, this.entityManager);
        }

        T currentEntity = dataManager.load((Class<T>) entity.getClass()).id(entity.getId());

        if(currentEntity == null){
            throw new GeneralBadRequest("Entity not found").setHttpStatus(HttpStatus.NOT_FOUND);
        }

        this.entityValidator.validateUniqueFields(entity);

        entity.setUpdateTs(new Date());
        entity.setVersion(currentEntity.getVersion() + 1);

        // TODO: Add maker checker actions

       this.entityManager.merge(entity);

       if(tUpdateEntityListener !=null){
           tUpdateEntityListener.afterUpdateEntity(entity, entityManager);
       }

        return entity;
    }

    @Override
    public <T extends StandardEntity> void performMakerCheckerChecks(T currentEntity, T update) throws GeneralBadRequest {
        if(currentEntity.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED){
            throw new GeneralBadRequest("Record has unapproved actions").setHttpStatus(HttpStatus.EXPECTATION_FAILED);
        }

        currentEntity.setAction(MakerCheckerAction.UPDATE);
        currentEntity.setActionStatus(MakerCheckerActionStatus.UNAPPROVED);
    }
}
