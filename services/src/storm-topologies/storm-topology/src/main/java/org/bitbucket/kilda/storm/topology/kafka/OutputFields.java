package org.bitbucket.kilda.storm.topology.kafka;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target(TYPE)
@Retention(RetentionPolicy.RUNTIME) // need to read at runtime
public @interface OutputFields {
	
	String[] value();

}
