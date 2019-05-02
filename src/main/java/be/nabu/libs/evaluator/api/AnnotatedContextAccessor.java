package be.nabu.libs.evaluator.api;

import java.util.Map;

import be.nabu.libs.evaluator.EvaluationException;

public interface AnnotatedContextAccessor<T> extends ContextAccessor<T> {
	public Map<String, Object> getAnnotation(T instance, String field, String annotation) throws EvaluationException;
}
