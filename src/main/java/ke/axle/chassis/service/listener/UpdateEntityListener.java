package ke.axle.chassis.service.listener;

import ke.axle.chassis.entity.base.StandardEntity;

import javax.persistence.EntityManager;

public interface UpdateEntityListener<T extends StandardEntity> {
    void beforeUpdateEntity(T entity, EntityManager entityManager);
    void afterUpdateEntity(T entity, EntityManager entityManager);
}
