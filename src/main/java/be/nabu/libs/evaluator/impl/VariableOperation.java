package be.nabu.libs.evaluator.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;

public class VariableOperation<T> extends BaseOperation<T> {
	
	private ContextAccessor<T> accessor = null;
	
	@Override
	public Object evaluate(T context) throws EvaluationException {
		return evaluate(context, 0);
	}
	
	@Override
	public void finish() {
		// do nothing
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object evaluate(T context, int offset) throws EvaluationException {
		Object object = null;
		
		// if you start off with an operation, you want to work from that result set
		if (offset == 0 && getParts().get(offset).getType() == QueryPart.Type.OPERATION) {
			object = ((Operation<T>) getParts().get(offset).getContent()).evaluate(context);
		}
		else {
			String path = getParts().get(offset).getContent().toString();
			// if it's not the first part, remove any leading "/"!
			if (offset > 0 && path.startsWith("/"))
				path = path.substring(1);
			object = getAccessor().get(context, path);
		}
		
		// it's null or you have reached the end, just return what you get
		if (object == null || offset == getParts().size() - 1) {
			return object;
		}		
		else if (object instanceof Map) {
			// you have defined an index on the map, get a specific key
			while (object instanceof Map && offset < getParts().size() - 1 && getParts().get(offset + 1).getType() == QueryPart.Type.OPERATION) {
				Object key = ((Operation<T>) getParts().get(offset + 1).getContent()).evaluate(context);
				object = ((Map) object).get(key);
				offset++;
			}
			// if the indexes were the last part, return the result
			if (offset == getParts().size() - 1) {
				return object;
			}
			// otherwise, keep evaluating
			else {
				return evaluate((T) object, offset + 1);
			}
		}
		// check if it's a list
		else if (object instanceof Collection || object instanceof Object[]) {
			// if the next element is an operation, it is indexed
			// if it returns a boolean, it has to be executed against each element in the list to filter
			// otherwise if it's a number, you need access to a single element
			boolean isConcatenatedResult = false;
			while ((object instanceof Collection || object instanceof Object[]) && offset < getParts().size() - 1 && getParts().get(offset + 1).getType() == QueryPart.Type.OPERATION) {
				object = JavaContextAccessor.listify(object);
				// we assume that indexed operations will be fixed indexes so it will be a native operation
				// this will not always be true but in a limited context (for which this is designed) this is the most likely scenario
				// note that if it is _only_ a variable, we assume the variable is also a number, would be odd to have a boolean variable
				if (((Operation<T>) getParts().get(offset + 1).getContent()).getType() == OperationType.NATIVE || ((Operation<T>) getParts().get(offset + 1).getContent()).getType() == OperationType.VARIABLE) {
					Number index = (Number) ((Operation<T>) getParts().get(offset + 1).getContent()).evaluate(context);
					object = index.intValue() < ((List) object).size() ? ((List) object).get(index.intValue()) : null;
				}
				else {
					isConcatenatedResult = true;
					List result = new ArrayList();
					for (Object child : (List) object) {
						// the operation must return a boolean for each item
						// if true, the item will be used for further evaluation
						Boolean useIt = (Boolean) ((Operation<T>) getParts().get(offset + 1).getContent()).evaluate((T) child);
						if (useIt != null && useIt) {
							result.add(child);
						}
					}
					object = result;
				}
				offset++;
			}
			// if the index was the last part, just return the child
			if (offset == getParts().size() - 1) {
				return object;
			}
			else {
				String childPath = getParts().get(offset + 1).getContent().toString();
				if (childPath.startsWith("/")) {
					childPath = childPath.substring(1);
				}
				// syntax wise you can do this:
				// myarray/$1
				// myarrayofarrays[$0 == 'test']/$1
				// the first one is indexed access to the array, the second one builds a result set in memory, then selects all the $1 from that resultset
				// the second basically returns a list of all possible "$1" values whereas the first selects the "$1" value for a specific array
				// this is why we have the boolean isConcatenatedResult that indicates which situation we are in
				if (((object instanceof Collection || object instanceof Object[]) && !isConcatenatedResult && childPath.matches("\\$[0-9]+")) || object instanceof Map) {
					object = getAccessor().get((T) object, childPath);
					if (offset == getParts().size() - 2) {
						return object;
					}
					// increase the offset so further evaluations take this into account
					else {
						offset++;
					}
				}
				if (object instanceof Collection || object instanceof Object[]) {
					List results = new ArrayList();
					// we just need to evaluate each subpart and add the result to the list
					for (Object child : JavaContextAccessor.listify(object)) {
						if (child != null) {
							Object childResult = evaluate((T) child, offset + 1);
							if (childResult instanceof List)
								results.addAll((List) childResult);
							// otherwise, add it (even if null!)
							else
								results.add(childResult);
						}
					}
					// return the list
					return results;
				}
				// otherwise, keep evaluating
				else {
					return evaluate((T) object, offset + 1);
				}
			}
		}
		// it's not a list, just recursively evaluate
		else {
			return evaluate((T) object, offset + 1);
		}
	}

	@Override
	public OperationType getType() {
		return OperationType.VARIABLE;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < getParts().size(); i++) {
			QueryPart part = getParts().get(i);
			if (part.getType() == Type.VARIABLE) {
				String string = part.getContent().toString();
				if (i > 0 && !string.startsWith("/")) {
					builder.append("/");
				}
				builder.append(string);
			}
			else if (part.getType() == Type.OPERATION) {
				if (i == 0) {
					builder.append(part.getContent());
				}
				else {
					builder.append("[" + part.getContent() + "]");
				}
			}
		}
		return builder.toString();
	}

	@SuppressWarnings("unchecked")
	public ContextAccessor<T> getAccessor() {
		if (accessor == null) {
			accessor = (ContextAccessor<T>) ContextAccessorFactory.getInstance().getAccessor();
		}
		return accessor;
	}

	public void setAccessor(ContextAccessor<T> accessor) {
		this.accessor = accessor;
	}
	
}
