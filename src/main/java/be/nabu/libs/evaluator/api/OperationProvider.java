package be.nabu.libs.evaluator.api;

public interface OperationProvider<T> {
	public enum OperationType {
		METHOD,
		VARIABLE,
		CLASSIC,
		NATIVE
	}
	
	public Operation<T> newOperation(OperationType type);
}
