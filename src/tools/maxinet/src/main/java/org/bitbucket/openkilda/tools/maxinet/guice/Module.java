package org.bitbucket.openkilda.tools.maxinet.guice;

import javax.ws.rs.client.WebTarget;

import org.bitbucket.openkilda.tools.maxinet.IMaxinet;
import org.bitbucket.openkilda.tools.maxinet.impl.Maxinet;
import org.bitbucket.openkilda.tools.maxinet.impl.WebTargetProvider;

import com.google.inject.AbstractModule;

public class Module extends AbstractModule {

	@Override
	protected void configure() {
        bind(WebTarget.class).toProvider(WebTargetProvider.class).asEagerSingleton();        
        bind(IMaxinet.class).to(Maxinet.class);
	}

}
