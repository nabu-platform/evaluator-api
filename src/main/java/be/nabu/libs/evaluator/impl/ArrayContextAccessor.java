package be.nabu.libs.evaluator.impl;

import java.util.Arrays;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ContextAccessor;

public class ArrayContextAccessor implements ContextAccessor<Object[]> {

	private CollectionContextAccessor accessor = new CollectionContextAccessor();
	
	@Override
	public Class<Object[]> getContextType() {
		return Object[].class;
	}

	@Override
	public boolean has(Object[] context, String name) throws EvaluationException {
		return context != null && accessor.has(Arrays.asList(context), name);
	}

	@Override
	public Object get(Object[] context, String name) throws EvaluationException {
		return context != null ? accessor.get(Arrays.asList(context), name) : null;
	}

}
