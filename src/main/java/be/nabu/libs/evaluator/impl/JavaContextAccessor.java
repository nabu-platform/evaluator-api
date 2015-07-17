package be.nabu.libs.evaluator.impl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ContextAccessor;

public class JavaContextAccessor implements ContextAccessor<Object> {
	
	private static Map<Class<?>, Map<String, Method>> getters = new HashMap<Class<?>, Map<String, Method>>();
	
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
			catch (NoSuchFieldException e) {
				throw new EvaluationException(e);
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
				throw new EvaluationException(e);
			}
			catch (NoSuchFieldException e) {
				throw new EvaluationException(e);
			}
			catch (SecurityException e) {
				throw new EvaluationException(e);
			}
			catch (InvocationTargetException e) {
				throw new EvaluationException(e);
			}
		}
		return null;
	}
	
}
