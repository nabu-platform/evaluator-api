package be.nabu.libs.evaluator;

import java.util.Collection;

import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.impl.JavaContextAccessor;

public class MultipleContextAccessor implements ContextAccessor<Object> {

	private Collection<ContextAccessor<?>> accessors;

	public MultipleContextAccessor(Collection<ContextAccessor<?>> accessors) {
		this.accessors = accessors;
	}
	
	@Override
	public Class<Object> getContextType() {
		return Object.class;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean has(Object context, String name) throws EvaluationException {
		if (context == null) {
			return false;
		}
		ContextAccessor accessor = getAccessor(context);
		if (accessor == null) {
			throw new EvaluationException("No accessor for: " + context);
		}
		return accessor.has(context, name);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object get(Object context, String name) throws EvaluationException {
		if (context == null) {
			return null;
		}
		ContextAccessor accessor = getAccessor(context);
		if (accessor == null) {
			throw new EvaluationException("No accessor for: " + context);
		}
		return accessor.get(context, name);
	}
	
	public ContextAccessor<?> getAccessor(Object object) {
		if (object == null) {
			return null;
		}
		ContextAccessor<?> mostSpecific = null;
		for (ContextAccessor<?> accessor : accessors) {
			if (accessor.getContextType().isAssignableFrom(object.getClass())) {
				if (mostSpecific == null || mostSpecific.getContextType().isAssignableFrom(accessor.getContextType())) {
					mostSpecific = accessor;
				}
			}
		}
		return mostSpecific == null ? new JavaContextAccessor() : mostSpecific;
	}
	
}
