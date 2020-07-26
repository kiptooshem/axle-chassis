package ke.axle.chassis.entity.base;

import javax.persistence.*;

@MappedSuperclass
public class BaseNumberEntity extends EntityInstance implements Entity<Long> {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.AUTO)
    protected Long id;


    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
