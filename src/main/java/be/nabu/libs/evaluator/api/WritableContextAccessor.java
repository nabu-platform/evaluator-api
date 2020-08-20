package be.nabu.libs.evaluator.api;

import be.nabu.libs.evaluator.EvaluationException;

public interface WritableContextAccessor<T> extends ContextAccessor<T> {
	public void set(T context, String name, Object value) throws EvaluationException;
}
