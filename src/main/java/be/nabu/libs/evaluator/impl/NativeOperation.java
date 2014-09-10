package be.nabu.libs.evaluator.impl;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;


/**
 * Can return the type of the operation
 * 
 * @author alex
 *
 */
public class NativeOperation<T> extends BaseOperation<T> {
	
	@Override
	public Object evaluate(T context) throws EvaluationException {
		return getParts().get(0).getContent();
	}
	@Override
	public void finish() {
		// do nothing
	}
	@Override
	public OperationType getType() {
		return OperationType.NATIVE;
	}
}
