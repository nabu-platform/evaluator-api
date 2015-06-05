package be.nabu.libs.evaluator.api;

import java.util.Collection;

public interface ListableContextAccessor<T> extends ContextAccessor<T> {
	public Collection<String> list(T object);
}
