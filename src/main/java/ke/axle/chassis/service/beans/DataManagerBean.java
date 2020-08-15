package ke.axle.chassis.service.beans;

import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.service.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

@Component
public class DataManagerBean implements DataManager {

    private EntityManager entityManager;

    @Autowired
    public DataManagerBean(EntityManager entityManager){
        this.entityManager = entityManager;
    }

    @Override
    public <T extends StandardEntity> QueryBuilder<T> load(Class<T> tClass) {
        return new QueryBuilder<T>(this.entityManager, tClass);
    }

    public class QueryBuilder<T extends StandardEntity>{

        private CriteriaBuilder criteriaBuilder;
        private CriteriaQuery<T> criteriaQuery;
        private EntityManager entityManager;
        private Root<T> root;
        public QueryBuilder(EntityManager entityManager, Class<T> tClass){
            this.entityManager = entityManager;
            this.criteriaBuilder = entityManager.getCriteriaBuilder();
            this.criteriaQuery = this.criteriaBuilder.createQuery(tClass);
            this.root = criteriaQuery.from(tClass);
        }

        // TODO: We don't really need this if we have repositories
        /*public QueryBuilder<T> where(String name, Object value){

        }*/

        public T id(Object id){
            criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.equal(root.get("id"), id),
                    criteriaBuilder.isNull(root.get("deleteTs"))));

            return this.one();
        }

        public T one(){

            try {
                return this.entityManager.createQuery(criteriaQuery).getSingleResult();
            }catch (NoResultException e ){
                return null;
            }

        }
    }
}
