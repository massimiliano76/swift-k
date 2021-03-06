/*
 * Swift Parallel Scripting Language (http://swift-lang.org)
 * Code from Java CoG Kit Project (see notice below) with modifications.
 *
 * Copyright 2005-2014 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jun 9, 2005
 */
package org.globus.cog.karajan.compiled.nodes.grid;

import k.rt.Stack;

import org.globus.cog.karajan.analyzer.ArgRef;
import org.globus.cog.karajan.analyzer.Param;
import org.globus.cog.karajan.compiled.nodes.functions.AbstractSingleValuedFunction;
import org.globus.cog.karajan.util.BoundContact;

public class Functions {

	public static class HostHasService extends AbstractSingleValuedFunction {
		private ArgRef<BoundContact> host;
		private ArgRef<String> type;
		private ArgRef<String> provider;

		@Override
		protected Param[] getParams() {
			return params("host", "type", "provider");
		}

		@Override
		public Object function(Stack stack) {
			BoundContact host = this.host.getValue(stack);
			String type = this.type.getValue(stack);
			String provider = this.provider.getValue(stack);
			if (host.hasService(BoundContact.getServiceType(type), provider)) {
				return true;
			}
			return false;
		}
		
	}
	
	public static class ServiceURI extends AbstractSingleValuedFunction {
		private ArgRef<BoundContact> host;
		private ArgRef<String> type;
		private ArgRef<String> provider;

		@Override
		protected Param[] getParams() {
			return params("host", "type", "provider");
		}

		@Override
		public Object function(Stack stack) {
			BoundContact host = this.host.getValue(stack);
			String type = this.type.getValue(stack);
			String provider = this.provider.getValue(stack);
			return host.getService(BoundContact.getServiceType(type), provider).getServiceContact().getContact();
		}
		
	}
}
