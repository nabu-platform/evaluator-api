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

package be.nabu.libs.evaluator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.impl.JavaContextAccessor;

public class MultipleContextAccessor implements ContextAccessor<Object> {

	private Collection<ContextAccessor<?>> accessors;
	private Map<Class<?>, ContextAccessor<?>> classAccessors = new HashMap<Class<?>, ContextAccessor<?>>();

	public MultipleContextAccessor(Collection<ContextAccessor<?>> accessors) {
		this.accessors = accessors;
	}
	
	@Override
	public Class<Object> getContextType() {
		return Object.class;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean has(Object context, String name) throws EvaluationException {
		if (context == null) {
			return false;
		}
		ContextAccessor accessor = getAccessor(context);
		if (accessor == null) {
			throw new EvaluationException("No accessor for: " + context);
		}
		return accessor.has(context, name);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean hasValue(Object context, String name) throws EvaluationException {
		if (context == null) {
			return false;
		}
		ContextAccessor accessor = getAccessor(context);
		if (accessor == null) {
			throw new EvaluationException("No accessor for: " + context);
		}
		return accessor.hasValue(context, name);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object get(Object context, String name) throws EvaluationException {
		if (context == null) {
			return null;
		}
		ContextAccessor accessor = getAccessor(context);
		if (accessor == null) {
			throw new EvaluationException("No accessor for: " + context);
		}
		return accessor.get(context, name);
	}
	
	public ContextAccessor<?> getAccessor(Object object) {
		if (object == null) {
			return null;
		}
		else if (!classAccessors.containsKey(object.getClass())) {
			ContextAccessor<?> mostSpecific = null;
			for (ContextAccessor<?> accessor : accessors) {
				if (accessor.getContextType().isAssignableFrom(object.getClass())) {
					if (mostSpecific == null || mostSpecific.getContextType().isAssignableFrom(accessor.getContextType())) {
						mostSpecific = accessor;
					}
				}
			}
			synchronized(classAccessors) {
				classAccessors.put(object.getClass(), mostSpecific == null ? new JavaContextAccessor() : mostSpecific);
			}
		}
		return classAccessors.get(object.getClass());
	}

	public Collection<ContextAccessor<?>> getAccessors() {
		return accessors;
	}
	
}
