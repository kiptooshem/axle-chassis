package ke.axle.chassis.service.listener;

import ke.axle.chassis.entity.base.StandardEntity;

import javax.persistence.EntityManager;

public interface DeleteEntityListener<T extends StandardEntity> {
    void beforeDeleteEntity(T entity, EntityManager entityManager);
    void afterDeleteEntity(T entity, EntityManager entityManager);
}
