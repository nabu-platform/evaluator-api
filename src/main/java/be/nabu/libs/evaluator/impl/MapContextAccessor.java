package be.nabu.libs.evaluator.impl;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

@SuppressWarnings("rawtypes")
public class MapContextAccessor implements ListableContextAccessor<Map>, WritableContextAccessor<Map> {

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

	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> list(Map object) {
		return object == null ? new ArrayList<String>() : new ArrayList<String>(object.keySet());
	}

	@SuppressWarnings("unchecked")
	@Override
	public void set(Map context, String name, Object value) {
		context.put(name, value);
	}

}
