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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.Methods;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

/**
 * All the methods that can be used must be static
 * Due to type erasure, you can't use STL but must resort to arrays to return multiple values
 * Also note you can't address methods in the "default" package
 * Instead all methods with no package declaration are referred to be.nabu.modules.structure.query.Methods
 * 
 * There are a few ways to determine which method you want:
 * 1) find the method that matches the return types of the target operations. This is by far the best and most specific but has a huge downside: you need to know the return types
 * when you call the resolve(). The evaluation path analyzer does not have enough information and giving it that information might limit the usability of the analyzer
 * You could resolve at first call and use whatever context was available then but this might make it odd if you use the same operation in multiple contexts with different parameters. You would get unexpected exceptions
 * 
 * 2) method that matches the number of return types, so you can still overload methods with a different number of arguments
 * The downside here is varargs, i'm not yet entirely sure how it will be used
 * 
 * 3) (as is) the most restrictive way is to simply check by name, that means each method must have a unique name within its class
 * The upside of this method is that i can expand to either of the above if deemed necessary.
 * 
 * @author alex
 *
 */
public class MethodOperation<T> extends BaseMethodOperation<T> {

	private List<Class<?>> defaultClasses = new ArrayList<Class<?>>();
	
	private Map<Integer, Method> methods = new HashMap<Integer, Method>();
	
	private boolean caseSensitive = true;
	
	private boolean allowNullCompletion = true;
	
	// allow access to any class
	private boolean allowAnyClass = true;
	
	private MethodFilter methodFilter;
	
	private Object context;
	
	public MethodOperation(Collection<Class<?>> classes) {
		this.defaultClasses.addAll(classes);
	}
	
	public MethodOperation(Class<?>...defaultClasses) {
		this.defaultClasses.addAll(Arrays.asList(defaultClasses));
	}
	public MethodOperation() {
		this(Methods.class);
	}
	
	@Override
	public void finish() throws ParseException {
		// do nothing
	}
	
	protected Method getMethod(int amountOfParams) throws ClassNotFoundException {
		if (!methods.containsKey(amountOfParams)) {
			synchronized(this) {
				if (!methods.containsKey(amountOfParams)) {
					String fullName = (String) getParts().get(0).getContent();
					methods.put(amountOfParams, findMethod(fullName, amountOfParams));
				}
			}
		}
		return methods.get(amountOfParams);
	}
	
	public Method findMethod(String fullName) throws ClassNotFoundException {
		return findMethod(fullName, -1);
	}
	
	private static Map<String, Method> methodMap = Collections.synchronizedMap(new HashMap<String, Method>());
	
	protected Method findMethod(String fullName, int amountOfParams) throws ClassNotFoundException {
		String methodId = fullName + "::" + defaultClasses + "::" + (context == null) + "::" + amountOfParams;
		if (!methodMap.containsKey(methodId)) {
			List<Class<?>> classesToCheck = new ArrayList<Class<?>>();
			String methodName = null;
			// if you access a specific class, use that
			if (fullName.contains(".")) {
				String namespace = fullName.replaceAll("^(.*)\\.[^.]+$", "$1");
				// check if it's a namespace of an existing class
				for (Class<?> possibleClass : defaultClasses) {
					MethodProviderClass annotation = possibleClass.getAnnotation(MethodProviderClass.class);
					if (annotation != null && annotation.namespace() != null && !annotation.namespace().isEmpty()) {
						if (namespace.equals(annotation.namespace())) {
							classesToCheck.add(possibleClass);
						}
					}
					else if (possibleClass.getName().replaceAll("\\.[^.]+$", "").equals(namespace)) {
						classesToCheck.add(possibleClass);
						break;
					}
				}
				// if it's not in the listed classes, try to load it
				if (allowAnyClass && classesToCheck.isEmpty()) {
					classesToCheck.add(Thread.currentThread().getContextClassLoader().loadClass(namespace));
				}
				methodName = fullName.replaceAll("^.*\\.([^.]+)$", "$1");
			}
			// otherwise, assume the default "methods" class
			else {
				classesToCheck.addAll(defaultClasses);
				methodName = fullName;
			}
			Method moreArgumentsMethod = null;
			for (Class<?> clazz : classesToCheck) {
				MethodProviderClass annotation = clazz.getAnnotation(MethodProviderClass.class);
				boolean caseSensitive = annotation != null ? annotation.caseSensitive() : this.caseSensitive;
				for (Method method : clazz.getDeclaredMethods()) {
					if (Modifier.isPublic(method.getModifiers()) && (context != null || Modifier.isStatic(method.getModifiers())) && ((caseSensitive && method.getName().equals(methodName)) || (!caseSensitive && method.getName().equalsIgnoreCase(methodName)))) {
						if (methodFilter != null && !methodFilter.isAllowed(method)) {
							continue;
						}
						// if the last parameter is an array, we will dynamically create an array at runtime
						// this is to support varargs
						if (amountOfParams < 0 || method.getParameterTypes().length == amountOfParams 
								|| (method.getParameterTypes().length < amountOfParams && method.getParameterTypes().length > 0 && method.getParameterTypes()[method.getParameterTypes().length - 1].isArray())
								// if the method has 1 parameter more than requested but it is an array, could be empty varargs
								|| (method.getParameterTypes().length == amountOfParams + 1 && method.getParameterTypes().length > 0 && method.getParameterTypes()[method.getParameterTypes().length - 1].isArray())) {
//							return method;
							moreArgumentsMethod = method;
							break;
						}
						// if the method has more arguments but we allow null completion, choose it
						else if (allowNullCompletion && amountOfParams >= 0 && method.getParameterTypes().length > amountOfParams) {
							moreArgumentsMethod = method;
						}
					}
				}
			}
			methodMap.put(methodId, moreArgumentsMethod);
		}
		return methodMap.get(methodId);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object evaluate(T context) throws EvaluationException {
		try {
			List arguments = new ArrayList();
			for (int i = 1; i < getParts().size(); i++) {
				Operation<T> argumentOperation = (Operation<T>) getParts().get(i).getContent();
				if (argumentOperation == null) {
					throw new EvaluationException("Can not find operation for method " + getParts().get(0) + " argument " + i + ": " + getParts().get(i));
				}
				arguments.add(argumentOperation.evaluate(context));
			}
			Method method = getMethod(arguments.size());
			if (method == null) {
				throw new EvaluationException("The method '" + getParts().get(0).getContent() + "' can not be resolved");
			}
			int amountOfParameters = method.getParameterTypes().length;
			// if we have fewer parameters than we have arguments, we're dealing with varargs
			if (amountOfParameters < arguments.size()) {
				Object [] newInstance = (Object[]) Array.newInstance(method.getParameterTypes()[amountOfParameters - 1].getComponentType(), arguments.size() - amountOfParameters + 1);
				for (int i = amountOfParameters - 1; i < arguments.size(); i++) {
					newInstance[i - (amountOfParameters - 1)] = arguments.get(i);
				}
				arguments = arguments.subList(0, amountOfParameters - 1);
				arguments.add(newInstance);
			}
			// if the amount of parameters is one larger that the arguments, we are using varargs but there is no argument to put in there
			// we need to add an empty array of the expected type
			else if (amountOfParameters == arguments.size() + 1 && method.isVarArgs()) {
				arguments.add(Array.newInstance(method.getParameterTypes()[amountOfParameters - 1], 0));
			}
			// if on the other hand we have fewer arguments than parameters, fill up with null
			else if (amountOfParameters > arguments.size()) {
				for (int i = arguments.size(); i < amountOfParameters; i++) {
					arguments.add(null);
				}
			}
			for (int i = 0; i < arguments.size(); i++) {
				// if it's an empty array and an array is requested, create a new one of the requested type
				// nothing has to be converted in this case
				if (arguments.get(i) instanceof Object[] && ((Object[]) arguments.get(i)).length == 0 && method.getParameterTypes()[i].isArray()) {
					arguments.set(i, Array.newInstance(method.getParameterTypes()[i].getComponentType(), 0));
				}
				else {
					arguments.set(i, ConverterFactory.getInstance().getConverter().convert(arguments.get(i), method.getParameterTypes()[i]));
				}
			}
			return method.invoke(this.context, arguments.toArray());
		}
		catch (IllegalAccessException e) {
			throw new EvaluationException(e);
		}
		catch (InvocationTargetException e) {
			throw new EvaluationException(e);
		}
		catch (ClassNotFoundException e) {
			throw new EvaluationException(e);
		}
	}

	public MethodFilter getMethodFilter() {
		return methodFilter;
	}
	public void setMethodFilter(MethodFilter methodFilter) {
		this.methodFilter = methodFilter;
	}

	public boolean isCaseSensitive() {
		return caseSensitive;
	}
	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}

	public Object getContext() {
		return context;
	}

	public void setContext(Object context) {
		this.context = context;
	}

	public static interface MethodFilter {
		public boolean isAllowed(Method method);
	}

	public boolean isAllowAnyClass() {
		return allowAnyClass;
	}

	public void setAllowAnyClass(boolean allowAnyClass) {
		this.allowAnyClass = allowAnyClass;
	}
}
