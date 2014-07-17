/*
 * Copyright 2012 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.griphyn.vdl.karajan.lib;

import k.rt.ExecutionException;
import k.rt.Stack;

import org.globus.cog.karajan.analyzer.ArgRef;
import org.globus.cog.karajan.analyzer.Signature;
import org.griphyn.vdl.mapping.InvalidPathException;
import org.griphyn.vdl.mapping.Path;
import org.griphyn.vdl.mapping.nodes.AbstractDataNode;

public class WaitFieldValue extends SwiftFunction {
	private ArgRef<AbstractDataNode> var;
    private ArgRef<Object> path; 
    
    @Override
    protected Signature getSignature() {
        return new Signature(params("var", optional("path", Path.EMPTY_PATH)));
    }


	/**
	 * Takes a supplied variable and path, and returns the unique value at that
	 * path. Path can contain wildcards, in which case an array is returned.
	 */
    @Override
	public Object function(Stack stack) {
		AbstractDataNode var = this.var.getValue(stack);
		try {
			Path path = parsePath(this.path.getValue(stack));
			var = (AbstractDataNode) var.getField(path);
			var.waitFor(this);
			return null;
		}
		catch (InvalidPathException e) {
			throw new ExecutionException(this, e);
		}
	}

}