package be.nabu.libs.evaluator.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;

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
	
	/**
	 * This allows missing variables to be resolved at any parent level
	 * Be careful as this naming conflicts are likely to arise in semi-complex situations
	 */
	private boolean allowParentLookup = false;
	
	/**
	 * This is more strict in that missing variables can be resolved against the root level if the current fails, NOT any intermediate parent
	 * You should still be careful with naming conflicts
	 */
	private boolean allowRootLookup = false;
	
	/**
	 * This stack keeps track (across operations) what the contexts are allowing you to refer to parent and root contexts
	 */
	private static ThreadLocal<Stack<?>> contextStack = new ThreadLocal<Stack<?>>();
	
	/**
	 * The "root" of a given context is not necessarily the actual root context, this allows you to define root stacks
	 * For example we have a glue script acting as an HTML page
	 * It calls a translation service that is provided by the nabu service stack, this adds the glue script as the root context because that is the very first execution in the thread
	 * The nabu service tries to evaluate a variable operation, but because we are still in that glue call, the glue script is set as the root and the nabu service can not resolve absolute paths to what it perceives as its root context
	 * In this case the nabu service will register a new root, indicating that for its execution we need the relative root that it provides
	 * This relative root will be used to resolve absolute paths
	 */
	private static ThreadLocal<Stack<Integer>> rootStack = new ThreadLocal<Stack<Integer>>();
	
	@Override
	public Object evaluate(T context) throws EvaluationException {
		return evaluate(context, 0);
	}
	
	@Override
	public void finish() {
		// do nothing
	}
	
	@SuppressWarnings("unchecked")
	protected boolean isNumericAccess(int offset) {
		return isNumericAccess((Operation<T>) getParts().get(offset).getContent());
//		return ((Operation<T>) getParts().get(offset).getContent()).getType() == OperationType.NATIVE || ((Operation<T>) getParts().get(offset).getContent()).getType() == OperationType.VARIABLE;
	}
	
	protected boolean isNumericAccess(Operation<T> operation) {
		if (operation.getType() == OperationType.NATIVE || operation.getType() == OperationType.VARIABLE) {
			return true;
		}
		else if (operation.getType() == OperationType.CLASSIC) {
			for (QueryPart part : operation.getParts()) {
				if (part.getType().isOperator()) {
					return part.getType() == QueryPart.Type.ADD
							|| part.getType() == QueryPart.Type.SUBSTRACT
							|| part.getType() == QueryPart.Type.MULTIPLY
							|| part.getType() == QueryPart.Type.DIVIDE
							|| part.getType() == QueryPart.Type.POWER
							|| part.getType() == QueryPart.Type.MOD;
				}
			}
		}
		return true;
	}
	
	private Object evaluate(T context, int offset) throws EvaluationException {
		getContextStack().push(context);
		try {
			return evaluate(offset);
		}
		finally {
			getContextStack().pop();
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object evaluate(int offset) throws EvaluationException {
		Object object = null;
		T context;
		Stack<T> contexts = getContextStack();
		int contextIndex = contexts.size() - 1;
		// if you start off with an operation, you want to work from that result set
		if (offset == 0 && getParts().get(offset).getType() == QueryPart.Type.OPERATION) {
			context = contexts.get(contextIndex); 
			object = ((Operation<T>) getParts().get(offset).getContent()).evaluate(context);
		}
		else {
			String path = getParts().get(offset).getContent().toString();
			// if the path is "." it does nothing, so we either simply evaluate the next (without adding context) or return the last context
			if (path.equals(".")) {
				if (offset >= getParts().size() - 1) {
					throw new EvaluationException("The path can't end with '.'");
				}
				return evaluate(offset + 1);
			}
			while (path.equals("..")) {
				if (offset >= getParts().size() - 1) {
					throw new EvaluationException("The path can't end with '..'");
				}
				if (contextIndex == 0) {
					throw new EvaluationException("Referencing an invalid context");
				}
				contextIndex--;
				offset++;
				path = getParts().get(offset).getContent().toString();
			}
			context = contexts.get(contextIndex);
			// if it's not the first part, remove any leading "/"!
			if (offset > 0 && path.startsWith("/")) {
				path = path.substring(1);
			}
			// go back to the root
			else if (offset == 0 && path.startsWith("/")) {
				contextIndex = getCurrentRoot();
				context = contexts.get(contextIndex);
				path = path.substring(1);
			}
			// you can reference the item itself by using "$this"
			object = "$this".equals(path) ? context : getAccessor().get(context, path);
			if (offset == 0 && object == null) {
				if (allowParentLookup) {
					while (object == null && contextIndex > 0) {
						contextIndex--;
						context = contexts.get(contextIndex);
						object = getAccessor().get(context, path);	
					}
				}
				else if (allowRootLookup && contextIndex > 0) {
					contextIndex = 0;
					context = contexts.get(contextIndex);
					object = getAccessor().get(context, path);	
				}
			}
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
		else if (object instanceof Collection || object instanceof Object[] || object instanceof Iterable) {
			// if the next element is an operation, it is indexed
			// if it returns a boolean, it has to be executed against each element in the list to filter
			// otherwise if it's a number, you need access to a single element
			boolean isConcatenatedResult = false;
			while ((object instanceof Collection || object instanceof Object[] || object instanceof Iterable) && offset < getParts().size() - 1 && getParts().get(offset + 1).getType() == QueryPart.Type.OPERATION) {
				// we assume that indexed operations will be fixed indexes so it will be a native operation
				// this will not always be true but in a limited context (for which this is designed) this is the most likely scenario
				// note that if it is _only_ a variable, we assume the variable is also a number, would be odd to have a boolean variable
				if (isNumericAccess(offset + 1)) {
					Number index = (Number) ((Operation<T>) getParts().get(offset + 1).getContent()).evaluate(context);
					if (object instanceof Iterable) {
						Iterator iterator = ((Iterable) object).iterator();
						for (long i = 0; i < index.longValue(); i++) {
							if (iterator.hasNext()) {
								iterator.next();
							}
							else {
								break;
							}
						}
						object = iterator.hasNext() ? iterator.next() : null;
						// resolve the object if it needs to
						if (object instanceof Callable) {
							try {
								object = ((Callable) object).call();
							}
							catch (Exception e) {
								throw new RuntimeException(e);
							}
						}
					}
					else {
						object = CollectionContextAccessor.listify(object);
						object = index.intValue() < ((List) object).size() ? ((List) object).get(index.intValue()) : null;
					}
				}
				// once we have a boolean selection instead of indexed access, we need to resolve the iterable
				else {
					object = CollectionContextAccessor.listify(object);
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
				while (((object instanceof Collection || object instanceof Object[] || object instanceof Iterable) && !isConcatenatedResult && childPath.matches("\\$[0-9]+")) || object instanceof Map) {
					if (object instanceof Iterable) {
						object = CollectionContextAccessor.listify(object);
					}
					object = getAccessor().get((T) object, childPath);
					if (offset == getParts().size() - 2) {
						return object;
					}
					// increase the offset so further evaluations take this into account
					else {
						offset++;
						childPath = getParts().get(offset + 1).getContent().toString();
						if (childPath.startsWith("/")) {
							childPath = childPath.substring(1);
						}
					}
				}
				if (object instanceof Collection || object instanceof Object[] || object instanceof Iterable) {
					List results = new ArrayList();
					// we just need to evaluate each subpart and add the result to the list
					for (Object child : CollectionContextAccessor.listify(object)) {
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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Stack<T> getContextStack() {
		if (contextStack.get() == null) {
			contextStack.set(new Stack());
		}
		return (Stack<T>) contextStack.get();
	}
	
	protected Integer getCurrentRoot() {
		if (rootStack.get() == null) {
			rootStack.set(new Stack<Integer>());
		}
		return rootStack.get().isEmpty() ? 0 : rootStack.get().peek();
	}

	public boolean isAllowParentLookup() {
		return allowParentLookup;
	}

	public void setAllowParentLookup(boolean allowParentLookup) {
		this.allowParentLookup = allowParentLookup;
	}

	public boolean isAllowRootLookup() {
		return allowRootLookup;
	}

	public void setAllowRootLookup(boolean allowRootLookup) {
		this.allowRootLookup = allowRootLookup;
	}
	
	/**
	 * The register root is called before the actual context to be root is added because it is before the evaluate() is originally called
	 * As such we add the "next" index to be added to the context stack as root
	 */
	public static void registerRoot() {
		// the "root" of the context stack is always considered a root, so don't do anything if there is no context or it is empty
		if (contextStack.get() != null && !contextStack.get().isEmpty()) {
			if (rootStack.get() == null) {
				rootStack.set(new Stack<Integer>());
			}
			rootStack.get().add(contextStack.get().size());
		}
	}
	
	public static void unregisterRoot() {
		// the "root" of the context stack is always considered a root, so don't do anything if there is no context or it only consists of one object
		if (rootStack.get() != null && !rootStack.get().isEmpty()) {
			rootStack.get().pop();
		}
	}
}
