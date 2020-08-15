package ke.axle.chassis.service;

import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.service.beans.DataManagerBean;

/**
 * This is necessary to maintain standard data management operations in case
 * the underlying API changes in the future, therefore, improving code maintainability.
 * EntityManager operations(preferably the generic queries) should run under this interface, but should also be exposed
 * so as to maintain flexibility of an application.
* */
public interface DataManager {
    <T extends StandardEntity> DataManagerBean.QueryBuilder<T> load(Class<T> tClass);
}
