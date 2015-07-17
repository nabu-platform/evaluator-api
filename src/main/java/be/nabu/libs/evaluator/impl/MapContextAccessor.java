package be.nabu.libs.evaluator.impl;

import java.util.Map;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ContextAccessor;

@SuppressWarnings("rawtypes")
public class MapContextAccessor implements ContextAccessor<Map> {

	@Override
	public Class<Map> getContextType() {
		return Map.class;
	}

	@Override
	public boolean has(Map context, String name) throws EvaluationException {
		return context.containsKey(name);
	}

	@Override
	public Object get(Map context, String name) throws EvaluationException {
		return context.get(name);
	}

}
