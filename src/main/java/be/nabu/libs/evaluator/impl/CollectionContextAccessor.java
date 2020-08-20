package be.nabu.libs.evaluator.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

@SuppressWarnings("rawtypes")
public class CollectionContextAccessor implements ContextAccessor<Collection>, WritableContextAccessor<Collection> {

	@Override
	public Class<Collection> getContextType() {
		return Collection.class;
	}

	@Override
	public boolean has(Collection context, String name) throws EvaluationException {
		return context != null && name.matches("\\$[0-9]+") && listify(context).size() > new Integer(name.substring(1));
	}

	@Override
	public Object get(Collection context, String name) throws EvaluationException {
		List listified = listify(context);
		return has(listified, name) ? listified.get(new Integer(name.substring(1))) : null;
	}
	
	@SuppressWarnings({ "unchecked" })
	public static List listify(Object object) {
		if (object instanceof List) {
			return (List) object;
		}
		else if (object instanceof Object[]) {
			return Arrays.asList((Object[]) object);
		}
		else if (object instanceof Collection) {
			return new ArrayList((Collection) object);
		}
		else if (object instanceof Iterable) {
			List list = new ArrayList();
			// non-parallel execution, you can resolve the iterable in a parallel way before running this code
			for (Object child : (Iterable) object) {
				if (child instanceof Callable) {
					try {
						list.add(((Callable) child).call());
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				else {
					list.add(child);
				}
			}
			return list;
		}
		throw new IllegalArgumentException("The object can not be converted to a list");
	}

	@SuppressWarnings("unchecked")
	@Override
	public void set(Collection context, String name, Object value) throws EvaluationException {
		// we expect the name to be $1 etc
		int index = name.startsWith("$") ? Integer.parseInt(name.substring(1)) : Integer.parseInt(name);
		
		// if the collection is smaller, we don't have to do anything special
		if (context.size() <= index) {
			// fill with nulls until we get there
			while (context.size() < index) {
				context.add(null);
			}
			// at position of index, we do this
			context.add(value);
		}
		// if it is a list, we can set in the correct position
		else if (context instanceof List) {
			((List) context).set(index, value);
		}
		// otherwise it gets heavy...
		else {
			List list = new ArrayList(context);
			list.set(index, value);
			context.removeAll(context);
			context.addAll(list);
		}
	}

}
