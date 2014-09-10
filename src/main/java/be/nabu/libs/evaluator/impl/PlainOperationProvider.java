package be.nabu.libs.evaluator.impl;

import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;

public class PlainOperationProvider implements OperationProvider<Object> {

	@Override
	public Operation<Object> newOperation(OperationType type) {
		switch(type) {
			case CLASSIC: return new ClassicOperation<Object>();
			case METHOD: return new MethodOperation<Object>();
			case VARIABLE: return new VariableOperation<Object>();
			case NATIVE: return new NativeOperation<Object>();
		}
		throw new RuntimeException("Unknown operation type: " + type);
	}
	
}
