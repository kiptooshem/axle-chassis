package ke.axle.chassis.service.beans;

import ke.axle.chassis.annotations.Unique;
import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.service.EntityFieldManager;
import ke.axle.chassis.utils.Extractor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;

@Component
public class EntityFieldManagerBean implements EntityFieldManager {

    private Map<String, EntityFields> allEntityFields;
    private Map<String, EntityFields> uniqueEntityFields;

    public EntityFieldManagerBean(){
        allEntityFields = new HashMap<String, EntityFields>();
        uniqueEntityFields = new HashMap<String, EntityFields>();
    }

    private <T extends StandardEntity> void extractFields(T entity){
        String entityName = entity.getClass().getSimpleName();
        List<Field> allFields = Extractor.getAllFields(entity.getClass());
        List<Field> uniqueFields = new ArrayList<>();

        for (Field field : allFields) {
            System.out.println(String.format("%s: Registered field '%s'", entityName, field.getName()));
            if (field.isAnnotationPresent(Unique.class)) {
                uniqueFields.add(field);
            }
        }

        this.allEntityFields.put(entityName, new EntityFields(entityName, allFields));
        this.uniqueEntityFields.put(entityName, new EntityFields(entityName, uniqueFields));
    }

    @Override
    public <T extends StandardEntity> List<Field> getAllFields(T entity) {
        String entityName = entity.getClass().getSimpleName();

        if(this.allEntityFields.containsKey(entityName)){
            return this.allEntityFields.get(entityName).getFields();
        }

        this.extractFields(entity);

        return this.allEntityFields.get(entityName).getFields();
    }

    @Override
    public <T extends StandardEntity> List<Field> getUniqueFields(T entity) {
        String entityName = entity.getClass().getSimpleName();

        if(this.uniqueEntityFields.containsKey(entityName)){
            return this.uniqueEntityFields.get(entityName).getFields();
        }

        this.extractFields(entity);

        return this.uniqueEntityFields.get(entityName).getFields();
    }

    /**
     * Used to hold fields of an entity during run time.
     * Generated the first time a request of an entity T is made. Subsequent request will just load the
     * object.
     * */
    protected static class EntityFields {
        private String entityName;
        private List<Field> fields;

        public EntityFields(String entityName){
            this.entityName = entityName;
            this.fields = new ArrayList<>();
        }

        public EntityFields(String entityName, List<Field> fields){
            this.entityName = entityName;
            this.fields = fields;
        }


        public String getEntityName() {
            return entityName;
        }

        public void setEntityName(String entityName) {
            this.entityName = entityName;
        }

        public List<Field> getFields() {
            return fields;
        }

        public void setFields(List<Field> fields) {
            this.fields = fields;
        }

        public void addField(Field field){
            this.fields.add(field);
        }

        public void addFields(Field... fields){
            this.fields.addAll(Arrays.asList(fields));
        }
    }
}
