package be.nabu.libs.evaluator.impl;

import java.util.Arrays;
import java.util.List;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;

public class ClassicOperation<T> extends BaseOperation<T> {
	
	private Converter converter;

	@Override
	public void finish() {
		// do nothing
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Object evaluate(T context) throws EvaluationException {
		for (int i = 0; i < getParts().size(); i++) {
			QueryPart part = getParts().get(i);
			// only interested in operators
			if (part.getType().isOperator()) {
				// get the operands
				Object left = part.getType().hasLeftOperand() ? getOperand(context, i - 1) : null;
			
				// the right operand does not always need to be calculated, check if the operator has a delay
				switch (part.getType()) {
					case LOGICAL_AND:
						if (!(Boolean) left)
							return false;
					break;
					case LOGICAL_OR:
						if ((Boolean) left)
							return true;
					break;
				}
				
				Object right = part.getType().hasRightOperand() ? getOperand(context, i + 1) : null;

				switch (part.getType()) {
					case ADD:
						// if the left one is a string, append
						if (left instanceof String)
							return ((String) left) + right;
						else if (left == null)
							throw new NullPointerException("The left operand of an ADD method was null");
						else if (left instanceof Integer)
							return ((Number) left).intValue() + ((Number) right).intValue();
						else if (left instanceof Long)
							return ((Number) left).longValue() + ((Number) right).longValue();
						else if (left instanceof Short)
							return ((Number) left).shortValue() + ((Number) right).shortValue();
						else if (left instanceof Float)
							return ((Number) left).floatValue() + ((Number) right).floatValue();
						else if(left instanceof Double)
							return ((Number) left).doubleValue() + ((Number) right).doubleValue();
						break;
					case SUBSTRACT:
						if (left instanceof Integer)
							return ((Number) left).intValue() - ((Number) right).intValue();
						else if (left instanceof Long)
							return ((Number) left).longValue() - ((Number) right).longValue();
						else if (left instanceof Short)
							return ((Number) left).shortValue() - ((Number) right).shortValue();
						else if (left instanceof Double)
							return ((Number) left).doubleValue() - ((Number) right).doubleValue();
						else if (left instanceof Float)
							return ((Number) left).floatValue() - ((Number) right).floatValue();
					case DIVIDE:
						if (left instanceof Integer)
							return ((Number) left).intValue() / ((Number) right).intValue();
						else if (left instanceof Long)
							return ((Number) left).longValue() / ((Number) right).longValue();
						else if (left instanceof Short)
							return ((Number) left).shortValue() / ((Number) right).shortValue();
						else if (left instanceof Double)
							return ((Number) left).doubleValue() / ((Number) right).doubleValue();
						else if (left instanceof Float)
							return ((Number) left).floatValue() / ((Number) right).floatValue();
					case MOD:
						if (left instanceof Integer)
							return ((Number) left).intValue() % ((Number) right).intValue();
						else if (left instanceof Long)
							return ((Number) left).longValue() % ((Number) right).longValue();
						else if (left instanceof Short)
							return ((Number) left).shortValue() % ((Number) right).shortValue();
						else if (left instanceof Double)
							return ((Number) left).doubleValue() % ((Number) right).doubleValue();
						else if (left instanceof Float)
							return ((Number) left).floatValue() % ((Number) right).floatValue();
					case MULTIPLY:
						if (left instanceof Integer)
							return ((Number) left).intValue() * ((Number) right).intValue();
						else if (left instanceof Long)
							return ((Number) left).longValue() * ((Number) right).longValue();
						else if (left instanceof Short)
							return ((Number) left).shortValue() * ((Number) right).shortValue();
						else if (left instanceof Double)
							return ((Number) left).doubleValue() * ((Number) right).doubleValue();
						else if (left instanceof Float)
							return ((Number) left).floatValue() * ((Number) right).floatValue();
					case POWER:
						return Math.pow(((Number) left).doubleValue(), ((Number) right).doubleValue());
					case BITWISE_AND:
						return (Boolean) left & (Boolean) right;
					case BITWISE_OR:
						return (Boolean) left | (Boolean) right;
					case LOGICAL_AND:
						return (Boolean) left && (Boolean) right;
					case LOGICAL_OR:
						return (Boolean) left || (Boolean) right;
					case EQUALS:
						if (left == null)
							return right == null ? true : false;
						else if (right == null)
							return false;
						else {
							// first convert to the proper type
							right = getConverter().convert(right, left.getClass());
							return left.equals(right);
						}
					case NOT_EQUALS:
						if (left == null)
							return right == null ? false : true;
						else if (right == null)
							return true;
						else {
							right = getConverter().convert(right, left.getClass());
							return !left.equals(right);
						}
					case GREATER:
						right = getConverter().convert(right, left.getClass());
						return ((Comparable) left).compareTo((Comparable) right) == 1;
					case GREATER_OR_EQUALS:
						right = getConverter().convert(right, left.getClass());
						return ((Comparable) left).compareTo((Comparable) right) >= 0;
					case LESSER:
						right = getConverter().convert(right, left.getClass());
						return ((Comparable) left).compareTo((Comparable) right) == -1;
					case LESSER_OR_EQUALS:
						right = getConverter().convert(right, left.getClass());
						return ((Comparable) left).compareTo((Comparable) right) <= 0;
					case IN:
						List<?> list1 = right instanceof List ? (List<?>) right : Arrays.asList((Object[]) right);
						return list1.contains(left);
					case NOT_IN:
						List<?> list2 = right instanceof List ? (List<?>) right : Arrays.asList((Object[]) right);
						return !list2.contains(left);
					case NOT:
						return !(Boolean) right;
					case MATCHES:
						return ((String) left).matches((String) right);
					case NOT_MATCHES:
						return !((String) left).matches((String) right);
					case NOT_XOR:
						return (Boolean) left.equals((Boolean) right);
					case XOR:
						return !(Boolean) left.equals((Boolean) right);
					case INCREASE:
						if (left instanceof Integer)
							return ((Number) left).intValue() + 1;
						else if (left instanceof Long)
							return ((Number) left).longValue() + 1;
						else if (left instanceof Short)
							return ((Number) left).shortValue() + 1;
						else if (left instanceof Double)
							return ((Number) left).doubleValue() + 1;
						else if (left instanceof Float)
							return ((Number) left).floatValue() + 1;
				}
			}
		}
		throw new EvaluationException("Could not evaluate the operator");
	}
	
	public Converter getConverter() {
		if (converter == null)
			converter = ConverterFactory.getInstance().getConverter();
		return converter;
	}

	public void setConverter(Converter converter) {
		this.converter = converter;
	}

	@SuppressWarnings("unchecked")
	private Object getOperand(T context, int position) throws EvaluationException {
		QueryPart part = getParts().get(position);
		if (part.getType().isNative())
			return part.getContent();
		else if (part.getType() == QueryPart.Type.OPERATION)
			return ((Operation<T>) part.getContent()).evaluate(context);
		else
			throw new EvaluationException("Expecting either a native part or an operation");
	}

	@Override
	public OperationType getType() {
		return OperationType.CLASSIC;
	}
}