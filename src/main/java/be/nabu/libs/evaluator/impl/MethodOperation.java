package be.nabu.libs.evaluator.impl;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.Methods;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;

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
public class MethodOperation<T> extends BaseOperation<T> {

	private List<Class<?>> defaultClasses = new ArrayList<Class<?>>();
	
	private Map<Integer, Method> methods = new HashMap<Integer, Method>();
	
	private boolean caseSensitive = true;
	
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
			String fullName = (String) getParts().get(0).getContent();
			methods.put(amountOfParams, findMethod(fullName, amountOfParams));
		}
		return methods.get(amountOfParams);
	}
	
	public Method findMethod(String fullName) throws ClassNotFoundException {
		return findMethod(fullName, -1);
	}
	
	protected Method findMethod(String fullName, int amountOfParams) throws ClassNotFoundException {
		List<Class<?>> classesToCheck = new ArrayList<Class<?>>();
		String methodName = null;
		// if you access a specific class, use that
		if (fullName.contains(".")) {
			classesToCheck.add(Class.forName(fullName.replaceAll("^(.*)\\.[^.]+$", "$1")));
			methodName = fullName.replaceAll("^.*\\.([^.]+)$", "$1");
		}
		// otherwise, assume the default "methods" class
		else {
			classesToCheck.addAll(defaultClasses);
			methodName = fullName;
		}
		for (Class<?> clazz : classesToCheck) {
			for (Method method : clazz.getDeclaredMethods()) {
				if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) && ((caseSensitive && method.getName().equals(methodName)) || (!caseSensitive && method.getName().equalsIgnoreCase(methodName)))) {
					// if the last parameter is an array, we will dynamically create an array at runtime
					// this is to support varargs
					if (amountOfParams < 0 || method.getParameterTypes().length == amountOfParams 
							|| (method.getParameterTypes().length < amountOfParams && method.getParameterTypes().length > 0 && method.getParameterTypes()[method.getParameterTypes().length - 1].isArray())
							// if the method has 1 parameter more than requested but it is an array, could be empty varargs
							|| (method.getParameterTypes().length == amountOfParams + 1 && method.getParameterTypes().length > 0 && method.getParameterTypes()[method.getParameterTypes().length - 1].isArray())) {
						return method;
					}
				}
			}
		}
		return null;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object evaluate(T context) throws EvaluationException {
		try {
			List arguments = new ArrayList();
			for (int i = 1; i < getParts().size(); i++) {
				Operation<T> argumentOperation = (Operation<T>) getParts().get(i).getContent();
				arguments.add(argumentOperation.evaluate(context));
			}
			Method method = getMethod(arguments.size());
			if (method == null) {
				throw new EvaluationException("The method '" + getParts().get(0).getContent() + "' can not be resolved");
			}
			// you got a method with fewer arguments than you have, so we need to create varargs
			int amountOfParameters = method.getParameterTypes().length;
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
			else if (amountOfParameters == arguments.size() + 1) {
				arguments.add(Array.newInstance(method.getParameterTypes()[amountOfParameters - 1], 0));
			}
			for (int i = 0; i < arguments.size(); i++) {
				arguments.set(i, ConverterFactory.getInstance().getConverter().convert(arguments.get(i), method.getParameterTypes()[i]));
			}
			return method.invoke(null, arguments.toArray());
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
	@Override
	public OperationType getType() {
		return OperationType.METHOD;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		// first the method name
		builder.append((String) getParts().get(0).getContent());
		// then the rest
		builder.append("(");
		for (int i = 1; i < getParts().size(); i++) {
			QueryPart part = getParts().get(i);
			if (i > 1) {
				builder.append(", ");
			}
			if (part.getType() == Type.STRING) {
				builder.append("\"" + part.getContent().toString() + "\"");
			}
			else {
				builder.append(part.getContent() == null ? "null" : part.getContent().toString());
			}
		}
		builder.append(")");
		return builder.toString();
	}
	public boolean isCaseSensitive() {
		return caseSensitive;
	}
	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}
}
