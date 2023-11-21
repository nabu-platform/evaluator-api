package be.nabu.libs.evaluator.api;

import be.nabu.libs.evaluator.EvaluationException;

public interface ContextAccessor<T> {
	public Class<T> getContextType();
	// check whether the context has a particular field
	public boolean has(T context, String name) throws EvaluationException;
	// Check if an object has a value for a given path
	// By default we assume if the type supports it, that the content has _a_ value (could be null)
	// If you have smarter implementations that can track changes, you can be more specific in your answer and state whether someone explicitly set a value
	public default boolean hasValue(T context, String name) throws EvaluationException {
		return has(context, name);
	}
	public Object get(T context, String name) throws EvaluationException;
}
