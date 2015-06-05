package be.nabu.libs.evaluator.api;

public interface WritableContextAccessor<T> extends ContextAccessor<T> {
	public void set(T context, String name, Object value);
}
