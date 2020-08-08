package ke.axle.chassis.entity.base;

import ke.axle.chassis.utils.UuidProvider;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseUuidEntity extends EntityInstance implements Entity<String> {

    @Id
    @Column(name = "ID")
    protected String id;

    public BaseUuidEntity(){
        UUID uuid = UuidProvider.createUuid();
        id = uuid.toString();
    }

    public String getId(){
        return id;
    }

    public void setId(String id){
        this.id = id;
    }

}
