package be.nabu.libs.evaluator.api;

import be.nabu.libs.evaluator.EvaluationException;

public interface ContextAccessor<T> {
	public Class<T> getContextType();
	public boolean has(T context, String name) throws EvaluationException;
	public Object get(T context, String name) throws EvaluationException;
}
