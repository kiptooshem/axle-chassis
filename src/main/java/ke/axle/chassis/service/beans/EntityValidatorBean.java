package ke.axle.chassis.service.beans;

import ke.axle.chassis.annotations.NickName;
import ke.axle.chassis.annotations.Unique;
import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.exceptions.GeneralBadRequest;
import ke.axle.chassis.service.EntityFieldManager;
import ke.axle.chassis.service.EntityValidator;
import ke.axle.chassis.utils.AppConstants;
import ke.axle.chassis.utils.Extractor;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.lang.reflect.Field;

@Component
public class EntityValidatorBean implements EntityValidator {

    EntityFieldManager entityFieldManager;
    EntityManager entityManager;

    @Autowired
    public EntityValidatorBean(EntityFieldManager entityFieldManager, EntityManager entityManager){
        this.entityFieldManager = entityFieldManager;
        this.entityManager = entityManager;
    }

    @Override
    public <T extends StandardEntity> void validateUniqueFields(T entity) throws GeneralBadRequest {

        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        Class<T> clazz = (Class<T>) entity.getClass();
        CriteriaBuilder criteriaBuilder = this.entityManager.getCriteriaBuilder();

        String id = entity.getId();

        for (Field field : entityFieldManager.getUniqueFields(entity)) {
            // TODO: This block can be refactored to use DataManager.
            // This will make code more maintainable in the future

            CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(clazz);
            Root<T> root = criteriaQuery.from(clazz);
            Unique unique = field.getDeclaredAnnotation(Unique.class);
            Object value = accessor.getPropertyValue(field.getName());

            if (id == null) {
                criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value),
                        criteriaBuilder.isNull(root.get("deleteTs")))));
            } else {
                criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value),
                        criteriaBuilder.isNull(root.get("deleteTs"))), criteriaBuilder.notEqual(root.get("id"), id)));
            }

            if (!this.entityManager.createQuery(criteriaQuery).getResultList().isEmpty()) {
                throw new GeneralBadRequest("Record with similar " + unique.fieldName() + " exists",HttpStatus.CONFLICT);
            }
        }
    }

    @Override
    public <T extends StandardEntity> void validateRelationsFields(T entity) throws GeneralBadRequest {
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        for (Field field : entityFieldManager.getAllFields(entity)) {
            if (field.isAnnotationPresent(ManyToOne.class)) {
                T relEntity = (T) accessor.getPropertyValue(field.getName());
                if (relEntity != null) {
                    Object id = relEntity.getId();

                    if (entityManager.find(relEntity.getClass(), id) == null) {
                        NickName nickName = relEntity.getClass().getDeclaredAnnotation(NickName.class);
                        if (nickName != null) {
                            throw new GeneralBadRequest(nickName.name() + " with id " + id + " doesn't exist",HttpStatus.NOT_FOUND);
                        } else {
                            throw new GeneralBadRequest("Record with id " + id + " doesn't exist",HttpStatus.NOT_FOUND);
                        }
                    }

                }
            }
        }
    }
}
