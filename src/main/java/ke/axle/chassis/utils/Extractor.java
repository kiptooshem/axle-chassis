package ke.axle.chassis.utils;

import java.lang.reflect.Field;
import java.util.*;

public class Extractor<T> {
    public List<String> extractFields(T t){
        List<String> extractedFields = new ArrayList<>();

        for(Field field : getAllFields(t.getClass())){
            extractedFields.add(field.getName());
        }

        return extractedFields;
    }

    public Map<String, Object> extractFieldsWithValues(T t) throws IllegalAccessException {

        Map<String, Object> value = new HashMap<>();

        for(Field field : getAllFields(t.getClass())){
            field.setAccessible(true);
            value.put(field.getName(), field.get(t));
        }

        return value;
    }

    public static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }

        System.out.println("Found fields: "+ fields);

        return fields;
    }
}
