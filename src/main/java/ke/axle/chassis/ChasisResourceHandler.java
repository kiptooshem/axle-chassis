package ke.axle.chassis;

import ke.axle.chassis.service.CreateEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ChasisResourceHandler {

    public static CreateEntityService createService;

    @Autowired
    public ChasisResourceHandler(CreateEntityService createEntityService){
        createService = createEntityService;
    }

}
