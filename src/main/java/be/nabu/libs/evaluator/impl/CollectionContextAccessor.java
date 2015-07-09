package be.nabu.libs.evaluator.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ContextAccessor;

@SuppressWarnings("rawtypes")
public class CollectionContextAccessor implements ContextAccessor<Collection> {

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
		throw new IllegalArgumentException("The object can not be converted to a list");
	}

}
