package be.nabu.libs.evaluator.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.Methods;
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

	private List<Class<?>> defaultClasses;
	
	public MethodOperation(Class<?>...defaultClasses) {
		this.defaultClasses.addAll(Arrays.asList(defaultClasses));
	}
	public MethodOperation() {
		this(Methods.class);
	}
	
	@Override
	public void finish() throws ParseException {
		if (!(getParts().get(0).getContent() instanceof Method)) {
			String fullName = (String) getParts().get(0).getContent();
			String methodName = null;
			try {
				List<Class<?>> classesToCheck = new ArrayList<Class<?>>();
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
				boolean methodFound = false;
				for (Class<?> clazz : classesToCheck) {
					for (Method method : clazz.getDeclaredMethods()) {
						if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(methodName)) {
							getParts().get(0).setContent(method);
							methodFound = true;
							break;
						}
					}
				}
				if (!methodFound)
					throw new ParseException("Could not find the method " + methodName + " in target class(es)", 0);
			}
			catch (ClassNotFoundException e) {
				throw new ParseException("Could not find the class: " + e.getMessage(), 0);
			}
			catch (SecurityException e) {
				throw new ParseException("Security exception: " + e.getMessage(), 0);
			}
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object evaluate(T context) throws EvaluationException {
		Method method = (Method) getParts().get(0).getContent();
		List arguments = new ArrayList();
		for (int i = 1; i < getParts().size(); i++) {
			Operation<T> argumentOperation = (Operation<T>) getParts().get(i).getContent();
			arguments.add(argumentOperation.evaluate(context));
		}
		try {
			return method.invoke(null, arguments.toArray());
		}
		catch (IllegalAccessException e) {
			throw new EvaluationException(e);
		}
		catch (IllegalArgumentException e) {
			throw new EvaluationException(e);
		}
		catch (InvocationTargetException e) {
			throw new EvaluationException(e);
		}
	}
	@Override
	public OperationType getType() {
		return OperationType.METHOD;
	}
}
