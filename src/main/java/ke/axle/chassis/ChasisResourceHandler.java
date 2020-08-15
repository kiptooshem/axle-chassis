package ke.axle.chassis;

import ke.axle.chassis.service.CreateEntityService;
import ke.axle.chassis.service.UpdateEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ChasisResourceHandler {

    public static CreateEntityService createService;
    public static UpdateEntityService updateService;

    @Autowired
    public ChasisResourceHandler(CreateEntityService createEntityService, UpdateEntityService updateEntityService){
        createService = createEntityService;
        updateService = updateEntityService;
    }

}
