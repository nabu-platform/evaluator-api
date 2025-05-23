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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.AnnotatedContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

public class JavaContextAccessor implements ListableContextAccessor<Object>, AnnotatedContextAccessor<Object>, WritableContextAccessor<Object> {
	
	private static Map<Class<?>, Map<String, Method>> getters = new HashMap<Class<?>, Map<String, Method>>();
	private static Map<Class<?>, Map<String, Method>> setters = new HashMap<Class<?>, Map<String, Method>>();
	
	private static Method getGetter(Class<?> clazz, String name) {
		if (!getters.containsKey(clazz)) {
			synchronized(getters) {
				if (!getters.containsKey(clazz)) {
					getters.put(clazz, new HashMap<String, Method>());
				}
			}
		}
		if (!getters.get(clazz).containsKey(name)) {
			synchronized(getters.get(clazz)) {
				if (!getters.get(clazz).containsKey(name)) {
					Method found = null;
					for (Method method : clazz.getMethods()) {
						if (method.getName().equals("get" + name.substring(0, 1).toUpperCase() + name.substring(1))) {
							found = method;
							break;
						}
					}
					getters.get(clazz).put(name, found);
				}
			}
		}
		return getters.get(clazz).get(name);
	}
	
	private static Method getSetter(Class<?> clazz, String name) {
		if (!setters.containsKey(clazz)) {
			synchronized(setters) {
				if (!setters.containsKey(clazz)) {
					setters.put(clazz, new HashMap<String, Method>());
				}
			}
		}
		if (!setters.get(clazz).containsKey(name)) {
			synchronized(setters.get(clazz)) {
				if (!setters.get(clazz).containsKey(name)) {
					Method found = null;
					for (Method method : clazz.getMethods()) {
						if (method.getName().equals("set" + name.substring(0, 1).toUpperCase() + name.substring(1))) {
							found = method;
							break;
						}
					}
					setters.get(clazz).put(name, found);
				}
			}
		}
		return setters.get(clazz).get(name);
	}
	
	private static Set<String> getAll(Class<?> clazz) {
		if (!getters.containsKey(clazz)) {
			synchronized(getters) {
				if (!getters.containsKey(clazz)) {
					Map<String, Method> map = new HashMap<String, Method>();
					List<String> ignore = Arrays.asList("getClass");
					for (Method method : clazz.getMethods()) {
						if (method.getName().startsWith("get") && method.getParameterCount() == 0 && ignore.indexOf(method.getName()) < 0) {
							String name = method.getName().substring("get".length());
							name = name.substring(0, 1).toLowerCase() + name.substring(1);
							map.put(name, method);
						}
					}
					getters.put(clazz, map);
				}
			}
		}
		return getters.get(clazz).keySet();
	}
	
	// annotations don't follow the bean spec
	private static Set<String> getAllAnnotation(Class<?> clazz) {
		if (!getters.containsKey(clazz)) {
			synchronized(getters) {
				if (!getters.containsKey(clazz)) {
					List<String> ignore = Arrays.asList("toString", "hashCode", "equals", "annotationType", "wait", "notify", "notifyAll", "getClass");
					Map<String, Method> map = new HashMap<String, Method>();
					for (Method method : clazz.getMethods()) {
						if (method.getParameterCount() == 0 && ignore.indexOf(method.getName()) < 0) {
							map.put(method.getName(), method);
						}
					}
					getters.put(clazz, map);
				}
			}
		}
		return getters.get(clazz).keySet();
	}
	
	@Override
	public Class<Object> getContextType() {
		return Object.class;
	}

	@Override
	public boolean has(Object context, String name) throws EvaluationException {
		if (context != null) {
			try {
				Method method = getGetter(context.getClass(), name);
				if (method != null) {
					return true;
				}
				else {
					return context.getClass().getDeclaredField(name) != null;
				}
			}
			// if we don't return false here, we can't do the java-method-as-lambda stuff... :(
			catch (NoSuchFieldException e) {
				return false;
			}
			catch (SecurityException e) {
				throw new EvaluationException(e);
			}
		}
		return false;
	}

	@Override
	public Object get(Object context, String name) throws EvaluationException {
		if (context != null) {
			try {
				Method method = getGetter(context.getClass(), name);
				if (method != null) {
					if (!method.isAccessible()) {
						method.setAccessible(true);
					}
					return method.invoke(context);
				}
				else {
					Field field = context.getClass().getDeclaredField(name);
					if (!field.isAccessible()) {
						field.setAccessible(true);
					}
					return field.get(context);
				}
			}
			catch (IllegalAccessException e) {
				throw new EvaluationException("Could not get field '" + name + "' in " + context.getClass(), e);
			}
			catch (NoSuchFieldException e) {
				throw new EvaluationException("Could not get field '" + name + "' in " + context.getClass(), e);
			}
			catch (SecurityException e) {
				throw new EvaluationException("Could not get field '" + name + "' in " + context.getClass(), e);
			}
			catch (InvocationTargetException e) {
				throw new EvaluationException("Could not get field '" + name + "' in " + context.getClass(), e);
			}
		}
		return null;
	}
	
	@Override
	public void set(Object context, String name, Object value) throws EvaluationException {
		if (context != null) {
			try {
				Method method = getSetter(context.getClass(), name);
				if (method != null) {
					if (!method.isAccessible()) {
						method.setAccessible(true);
					}
					method.invoke(context, value);
				}
				else {
					Field field = context.getClass().getDeclaredField(name);
					if (!field.isAccessible()) {
						field.setAccessible(true);
					}
					field.set(context, value);
				}
			}
			catch (Exception e) {
				throw new EvaluationException("Could not set field '" + name + "' in " + context.getClass(), e);
			}
		}
	}
	

	@Override
	public Collection<String> list(Object object) {
		List<String> list = new ArrayList<String>();
		if (object != null) {
			list.addAll(getAll(object.getClass()));
		}
		return list;
	}

	@Override
	public Map<String, Object> getAnnotation(Object instance, String field, String annotation) throws EvaluationException {
		if (instance != null) {
			Method getter = getGetter(instance.getClass(), field);
			if (getter != null) {
				for (Annotation potential : getter.getAnnotations()) {
					if (potential.annotationType().getName().equals(annotation) || potential.annotationType().getSimpleName().equals(annotation)) {
						Map<String, Object> map = new HashMap<String, Object>();
						for (String annotationField : getAllAnnotation(potential.getClass())) {
							map.put(annotationField, get(potential, annotationField));
						}
						return map;
					}
				}
			}
		}
		return null;
	}

}
