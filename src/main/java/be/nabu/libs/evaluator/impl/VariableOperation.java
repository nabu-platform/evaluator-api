/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.evaluator.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;

import be.nabu.libs.converter.ConverterFactory;
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
	 * Suppose you have this
	 * 
	 * a = [ [1, 2], [2, 3] ]
	 * 
	 * If you print out a[0], you get [1, 2], if you print out a/$0 (with this option turned off), you get the same output [1, 2].
	 * So this access is identical. And it is mostly meant for subqueries like this:
	 * 
	 * a[$0 == 2 || $1 == 2][0]
	 * 
	 * This will return [1,2] which is the first row that matches the given query
	 * Here you want to reference indexed items in non-indexed way.
	 * 
	 * There is however a discrepancy, if you do this:
	 * 
	 * a[$0 == 2 || $1 == 2]/$0
	 * 
	 * This will return [1,1]! It actually creates a list of all the first columns of the rows that match
	 * 
	 * The feature to draw columns from dimensional objects is very interesting, so first instinct was to make it global so a/$0 also draws a list of columns.
	 * If you enable the following option, you get this behavior.
	 * 
	 * HOWEVER, it will also disable the use of indexed access in queries, a[$0 == 2], here $0 would expand to include all the first columns of that particular record, so for instance [1] rather than the original behavior which was 1.
	 */
	public static boolean alwaysUseConcatenationForDollarIndex = Boolean.parseBoolean(System.getProperty("glue.alwaysUseConcatenationForDollarIndex", "false"));
	/**
	 * This option fixes the other way: we never generate columns with $0 access
	 */
	public static boolean neverUseConcatenationForDollarIndex = Boolean.parseBoolean(System.getProperty("glue.neverUseConcatenationForDollarIndex", "true"));
	
	/**
	 * The "root" of a given context is not necessarily the actual root context, this allows you to define root stacks
	 * For example we have a glue script acting as an HTML page
	 * It calls a translation service that is provided by the nabu service stack, this adds the glue script as the root context because that is the very first execution in the thread
	 * The nabu service tries to evaluate a variable operation, but because we are still in that glue call, the glue script is set as the root and the nabu service can not resolve absolute paths to what it perceives as its root context
	 * In this case the nabu service will register a new root, indicating that for its execution we need the relative root that it provides
	 * This relative root will be used to resolve absolute paths
	 */
	private static ThreadLocal<Stack<Integer>> rootStack = new ThreadLocal<Stack<Integer>>();
	
	private boolean isCollectionIterable(Object object) {
		if (object == null) {
			return false;
		}
		for (Class<?> ifaceClass : object.getClass().getInterfaces()) {
			if (ifaceClass.getName().equals("be.nabu.glue.core.api.CollectionIterable")) {
				return true;
			}
		}
		return false;
	}
	
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
			// you can do something like a[true] which simply selects all a
			if ("true".equals(operation.toString()) || "false".equals(operation.toString())) {
				return false;
			}
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
		return false;
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
	
	public static Object getCurrentContext() {
		return contextStack == null || contextStack.get() == null || contextStack.get().isEmpty() ? null : contextStack.get().peek();
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
		
		// @2023-03-15: if we want to do queried list access (so NOT numeric), we want an empty collection instead of null
		if (object == null) {
			if (offset < getParts().size() - 1 && getParts().get(offset + 1).getType() == QueryPart.Type.OPERATION && !isNumericAccess(offset + 1)) {
				return new ArrayList();
			}
			else {
				return null;
			}
		}
		// it's null or you have reached the end, just return what you get
		if (object == null || offset == getParts().size() - 1) {
			return object;
		}		
		if (object instanceof Map) {
			// you have defined an index on the map, get a specific key
			while (object instanceof Map && offset < getParts().size() - 1 && getParts().get(offset + 1).getType() == QueryPart.Type.OPERATION) {
				Object key = ((Operation<T>) getParts().get(offset + 1).getContent()).evaluate(context);
				// if the key is not in the map and the first key of the map is a string, we assume all keys are strings and convert to it
				// this can be broadened to support other types as well but the primary usecase is currently nabu where maps are only used with string keys
				if (!((Map) object).containsKey(key) && ((Map) object).size() > 0 && ((Map) object).keySet().iterator().next() instanceof String) {
					key = ConverterFactory.getInstance().getConverter().convert(key, String.class);
				}
				object = ((Map) object).get(key);
				offset++;
			}
		}
		// check if it's a list
		if (object instanceof Collection || object instanceof Object[] || isCollectionIterable(object)) {
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
					if (index == null) {
						throw new IllegalArgumentException("The index can not be null: " + getParts().get(offset + 1).getContent());
					}
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
					// once you have added numeric access, it is no longer concatenated
					isConcatenatedResult = false;
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
			if (offset < getParts().size() - 1) {
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
				while (((object instanceof Collection || object instanceof Object[] || object instanceof Iterable) && (!isConcatenatedResult || neverUseConcatenationForDollarIndex) && !alwaysUseConcatenationForDollarIndex && childPath.matches("^\\$[0-9]+$")) || object instanceof Map) {
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
		// if the indexes were the last part, return the result
		if (offset == getParts().size() - 1) {
			return object;
		}
		// the next part is an operation but it is not a map or a list, try contextual access
		else if (offset < getParts().size() - 1 && getParts().get(offset + 1).getType() == QueryPart.Type.OPERATION) {
			// you have defined an index on the map, get a specific key
			while (offset < getParts().size() - 1 && getParts().get(offset + 1).getType() == QueryPart.Type.OPERATION) {
				Object key = ((Operation<T>) getParts().get(offset + 1).getContent()).evaluate(context);
				if (key == null) {
					throw new EvaluationException("Could not resolve key: " + getParts().get(offset + 1).getContent());
				}
				object = getAccessor().get((T) object, key.toString());
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
