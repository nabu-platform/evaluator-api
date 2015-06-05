package be.nabu.libs.evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.libs.evaluator.api.ContextAccessor;

public class ContextAccessorFactory {
	
	private static ContextAccessorFactory instance;
	
	public static ContextAccessorFactory getInstance() {
		if (instance == null) {
			synchronized(ContextAccessorFactory.class) {
				if (instance == null) {
					instance = new ContextAccessorFactory();
				}
			}
		}
		return instance;
	}
	
	private List<ContextAccessor<?>> accessors;
	private MultipleContextAccessor accessor;
	
	public void addAccessor(ContextAccessor<?> accessor) {
		accessors.add(accessor);
	}
	public void removeAccessor(ContextAccessor<?> accessor) {
		accessors.remove(accessor);
	}
	
	private List<ContextAccessor<?>> getAccessors() {
		if (accessors == null) {
			synchronized(this) {
				if (accessors == null) {
					List<ContextAccessor<?>> accessors = new ArrayList<ContextAccessor<?>>();
					for (ContextAccessor<?> accessor : ServiceLoader.load(ContextAccessor.class)) {
						accessors.add(accessor);
					}
					this.accessors = accessors;
				}
			}
		}
		return accessors;
	}
	
	@SuppressWarnings("unused")
	private void activate() {
		instance = this;
	}
	@SuppressWarnings("unused")
	private void deactivate() {
		instance = null;
	}
	
	public ContextAccessor<?> getAccessor() {
		if (accessor == null) {
			synchronized(this) {
				if (accessor == null) {
					accessor = new MultipleContextAccessor(getAccessors());
				}
			}
		}
		return accessor;
	}
	
}
