package be.nabu.libs.evaluator;

import java.text.ParseException;

import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;

public class EvaluationUtils {
	
	@SuppressWarnings("unchecked")
	public static <T> Operation<T> clone(Operation<T> operation, OperationProvider<T> provider) throws ParseException {
		Operation<T> clone = provider.newOperation(operation.getType());
		for (QueryPart part : operation.getParts()) {
			// if the part is a nested operation, clone it
			if (part.getContent() instanceof Operation) {
				clone.add(new QueryPart(part.getToken(), part.getType(), clone((Operation<T>) part.getContent(), provider)));
			}
			else {
				clone.add(part.clone());
			}
		}
		clone.finish();
		return clone;
	}
}
