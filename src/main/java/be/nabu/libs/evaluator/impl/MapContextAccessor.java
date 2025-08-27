/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.evaluator.impl;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

@SuppressWarnings("rawtypes")
public class MapContextAccessor implements ListableContextAccessor<Map>, WritableContextAccessor<Map> {

	@Override
	public Class<Map> getContextType() {
		return Map.class;
	}

	@Override
	public boolean has(Map context, String name) throws EvaluationException {
		return context.containsKey(name);
	}

	@Override
	public Object get(Map context, String name) throws EvaluationException {
		return context.get(name);
	}

	@Override
	public boolean hasValue(Map context, String name) throws EvaluationException {
		return context.containsKey(name);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> list(Map object) {
		return object == null ? new ArrayList<String>() : new ArrayList<String>(object.keySet());
	}

	@SuppressWarnings("unchecked")
	@Override
	public void set(Map context, String name, Object value) {
		context.put(name, value);
	}

}
