package ke.axle.chassis.service.listener;

import ke.axle.chassis.entity.base.StandardEntity;

import javax.persistence.EntityManager;

public interface CreateEntityListener<T extends StandardEntity> {
    void beforeCreateEntity(T entity, EntityManager entityManager);
    void afterCreateEntity(T entity, EntityManager entityManager);
}
