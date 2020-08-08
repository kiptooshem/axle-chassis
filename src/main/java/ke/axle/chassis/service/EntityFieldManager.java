package ke.axle.chassis.service;

import ke.axle.chassis.entity.base.StandardEntity;

import java.lang.reflect.Field;
import java.util.List;

public interface EntityFieldManager {
    <T extends StandardEntity> List<Field> getAllFields(T entity);
    <T extends StandardEntity> List<Field> getUniqueFields(T entity);
}
