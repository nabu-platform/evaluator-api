package be.nabu.libs.evaluator.base;

import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;

abstract public class BaseMethodOperation<T> extends BaseOperation<T> {

	@Override
	public OperationType getType() {
		return OperationType.METHOD;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		// first the method name
		builder.append((String) getParts().get(0).getContent());
		// then the rest
		builder.append("(");
		for (int i = 1; i < getParts().size(); i++) {
			QueryPart part = getParts().get(i);
			if (i > 1) {
				builder.append(", ");
			}
			if (part.getType() == Type.STRING) {
				builder.append("\"" + part.getContent().toString() + "\"");
			}
			else {
				builder.append(part.getContent() == null ? "null" : part.getContent().toString());
			}
		}
		builder.append(")");
		return builder.toString();
	}
}
