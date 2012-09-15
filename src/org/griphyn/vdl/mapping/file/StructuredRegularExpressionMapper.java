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


package org.griphyn.vdl.mapping.file;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.griphyn.vdl.mapping.AbsFile;
import org.griphyn.vdl.mapping.DSHandle;
import org.griphyn.vdl.mapping.HandleOpenException;
import org.griphyn.vdl.mapping.InvalidPathException;
import org.griphyn.vdl.mapping.MappingParam;
import org.griphyn.vdl.mapping.MappingParamSet;
import org.griphyn.vdl.mapping.Path;
import org.griphyn.vdl.mapping.PhysicalFormat;

public class StructuredRegularExpressionMapper extends AbstractFileMapper {

        public static final Logger logger =
            Logger.getLogger(StructuredRegularExpressionMapper.class);

        public static final MappingParam PARAM_SOURCE = new MappingParam("source");
	public static final MappingParam PARAM_MATCH = new MappingParam("match");
	public static final MappingParam PARAM_TRANSFORM = new MappingParam("transform");

	public StructuredRegularExpressionMapper() {
	}

	public void setParams(MappingParamSet params) {
		super.setParams(params);
		if (!PARAM_MATCH.isPresent(this)) {
			throw new RuntimeException("Missing parameter match!");
		}
	}

	public Collection<Path> existing() {

		DSHandle sourceHandle = (DSHandle) PARAM_SOURCE.getRawValue(this);

		Collection<Path> output = new ArrayList<Path>();
		Collection<DSHandle> sourceFields;
		try {
			sourceFields = sourceHandle.getFields(Path.CHILDREN);
		}
		catch (InvalidPathException ipe) {
			return Collections.emptyList();
		}
		catch (HandleOpenException hoe) {
			throw new RuntimeException(
					"Handle open. Throwing this exception may not be the right thing to do. TODO");
		}
		Iterator<DSHandle> i = sourceFields.iterator();
		while (i.hasNext()) {
			DSHandle f = i.next();
			output.add(f.getPathFromRoot());
		}

		return output;
	}

	public PhysicalFormat map(Path path) {

                logger.debug("map(): path: " + path); 

		String match = PARAM_MATCH.getStringValue(this);
		String transform = PARAM_TRANSFORM.getStringValue(this);

		DSHandle sourceHandle = (DSHandle) PARAM_SOURCE.getRawValue(this);
		DSHandle hereHandle;
		try {
			hereHandle = sourceHandle.getField(path);
		}
		catch (InvalidPathException ipe) {
			throw new RuntimeException("Cannot get requested path " + path
					+ " from source data structure");
		}

		PhysicalFormat source = hereHandle.getRoot().getMapper().map(hereHandle.getPathFromRoot());
		if (!source.getType().equals("file")) {
			throw new RuntimeException(
					"Cannot use the regular expression mapper with a source that has a mapper that is not file-based");
		}
		Pattern p = Pattern.compile(match);
		Matcher m = p.matcher(((AbsFile) source).getPath());
		if (!m.find()) {
			throw new RuntimeException("No match found! source='" + source + "' match = '" + match
					+ "'");
		}
		// find group number to replace
		Pattern p2 = Pattern.compile("(\\\\\\d)");
		Matcher m2 = p2.matcher(transform);
		StringBuffer sb = new StringBuffer();
		while (m2.find()) {
			String group = m2.group(1);
			int g = Integer.parseInt(group.substring(1));
			m2.appendReplacement(sb, m.group(g));
		}
		m2.appendTail(sb);
		return new AbsFile(sb.toString());
	}

	public Path rmap(String name) {
		return Path.EMPTY_PATH;
	}

	public boolean isStatic() {
		return true;
	}

}
