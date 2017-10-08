/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
