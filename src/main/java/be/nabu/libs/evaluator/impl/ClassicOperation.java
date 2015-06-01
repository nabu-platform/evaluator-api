package be.nabu.libs.evaluator.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.api.operations.And;
import be.nabu.libs.evaluator.api.operations.Div;
import be.nabu.libs.evaluator.api.operations.Minus;
import be.nabu.libs.evaluator.api.operations.Mod;
import be.nabu.libs.evaluator.api.operations.Multiply;
import be.nabu.libs.evaluator.api.operations.Next;
import be.nabu.libs.evaluator.api.operations.Or;
import be.nabu.libs.evaluator.api.operations.Plus;
import be.nabu.libs.evaluator.api.operations.Power;
import be.nabu.libs.evaluator.api.operations.Previous;
import be.nabu.libs.evaluator.api.operations.Xor;
import be.nabu.libs.evaluator.base.BaseOperation;

public class ClassicOperation<T> extends BaseOperation<T> {
	
	private Converter converter;

	@Override
	public void finish() {
		// do nothing
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes", "incomplete-switch" })
	@Override
	public Object evaluate(T context) throws EvaluationException {
		for (int i = 0; i < getParts().size(); i++) {
			QueryPart part = getParts().get(i);
			// only interested in operators
			if (part.getType().isOperator()) {
				// get the operands
				Object left = part.getType().hasLeftOperand() ? getOperand(context, i - 1) : null;
			
				// don't get (and potentially evaluate) the right part if it's not necessary
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
						if (left instanceof Plus) {
							return ((Plus) left).plus(right);
						}
						else if (left == null) {
							throw new NullPointerException("The left operand of an ADD method was null");
						}
						right = getConverter().convert(right, left.getClass());
						// if the left one is a string, append
						if (left instanceof String)
							return ((String) left) + right;
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
						if (left instanceof Minus) {
							return ((Minus) left).minus(right);
						}
						if (!(left instanceof Number)) {
							left = getConverter().convert(left, Double.class);
						}
						right = getConverter().convert(right, left.getClass());
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
						if (left instanceof Div) {
							return ((Div) left).div(right);
						}
						right = getConverter().convert(right, left.getClass());
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
						if (left instanceof Mod) {
							return ((Mod) left).mod(right);
						}
						right = getConverter().convert(right, left.getClass());
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
						if (left instanceof Multiply) {
							return ((Multiply) left).multiply(right);
						}
						right = getConverter().convert(right, left.getClass());
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
						if (left instanceof Power) {
							return ((Power) left).power(right);
						}
						right = getConverter().convert(right, left.getClass());
						return Math.pow(((Number) left).doubleValue(), ((Number) right).doubleValue());
					case BITWISE_AND:
						if (left instanceof And) {
							return ((And) left).and(right);
						}
						return getConverter().convert(left, Boolean.class) & getConverter().convert(right, Boolean.class);
					case BITWISE_OR:
						if (left instanceof Or) {
							return ((Or) left).or(right);
						}
						right = getConverter().convert(right, left.getClass());
						return getConverter().convert(left, Boolean.class) | getConverter().convert(right, Boolean.class);
					case LOGICAL_AND:
						return getConverter().convert(left, Boolean.class) && getConverter().convert(right, Boolean.class);
					case LOGICAL_OR:
						return getConverter().convert(left, Boolean.class) || getConverter().convert(right, Boolean.class);
					case EQUALS:
						if (left == null) {
							return right == null ? true : false;
						}
						else if (right == null) {
							return false;
						}
						else {
							right = getConverter().convert(right, left.getClass());
							return left.equals(right);
						}
					case NOT_EQUALS:
						if (left == null) {
							return right == null ? false : true;
						}
						else if (right == null) {
							return true;
						}
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
						if (right instanceof String) {
							left = getConverter().convert(left, String.class);
							return ((String) right).toLowerCase().contains(((String) left).toLowerCase());
						}
						else {
							List<?> list1 = right instanceof Collection ? new ArrayList((List<?>) right) : Arrays.asList((Object[]) right);
							return list1.contains(left);
						}
					case NOT_IN:
						if (right instanceof String) {
							left = getConverter().convert(left, String.class);
							return !((String) right).toLowerCase().contains(((String) left).toLowerCase());
						}
						else {
							List<?> list2 = right instanceof Collection ? new ArrayList((List<?>) right) : Arrays.asList((Object[]) right);
							return !list2.contains(left);
						}
					case NOT:
						return !getConverter().convert(right, Boolean.class);
					case MATCHES:
						left = getConverter().convert(left, String.class);
						right = getConverter().convert(right, String.class);
						return ((String) left).matches((String) right);
					case NOT_MATCHES:
						left = getConverter().convert(left, String.class);
						right = getConverter().convert(right, String.class);
						return !((String) left).matches((String) right);
					case NOT_XOR:
						left = getConverter().convert(left, Boolean.class);
						right = getConverter().convert(right, Boolean.class);
						return (Boolean) left.equals((Boolean) right);
					case XOR:
						if (left instanceof Xor) {
							return ((Xor) left).xor(right);
						}
						left = getConverter().convert(left, Boolean.class);
						right = getConverter().convert(right, Boolean.class);
						return !(Boolean) left.equals((Boolean) right);
					case INCREASE:
						if (left instanceof Next) {
							return ((Next) left).next();
						}
						if (left instanceof Integer) {
							return ((Number) left).intValue() + 1;
						}
						else if (left instanceof Long) {
							return ((Number) left).longValue() + 1;
						}
						else if (left instanceof Short) {
							return ((Number) left).shortValue() + 1;
						}
						else if (left instanceof Double) {
							return ((Number) left).doubleValue() + 1;
						}
						else if (left instanceof Float) {
							return ((Number) left).floatValue() + 1;
						}
					case DECREASE:
						if (left instanceof Previous) {
							return ((Previous) left).previous();
						}
						if (left instanceof Integer) {
							return ((Number) left).intValue() - 1;
						}
						else if (left instanceof Long) {
							return ((Number) left).longValue() - 1;
						}
						else if (left instanceof Short) {
							return ((Number) left).shortValue() - 1;
						}
						else if (left instanceof Double) {
							return ((Number) left).doubleValue() - 1;
						}
						else if (left instanceof Float) {
							return ((Number) left).floatValue() - 1;
						}
				}
			}
		}
		throw new EvaluationException("Could not evaluate the operator");
	}
	
	public Converter getConverter() {
		if (converter == null) {
			converter = ConverterFactory.getInstance().getConverter();
		}
		return converter;
	}

	public void setConverter(Converter converter) {
		this.converter = converter;
	}

	@SuppressWarnings("unchecked")
	protected Object getOperand(T context, int position) throws EvaluationException {
		QueryPart part = getParts().get(position);
		if (part.getType().isNative()) {
			return part.getContent();
		}
		else if (part.getType() == QueryPart.Type.OPERATION) {
			return ((Operation<T>) part.getContent()).evaluate(context);
		}
		else {
			throw new EvaluationException("Expecting either a native part or an operation");
		}
	}

	@Override
	public OperationType getType() {
		return OperationType.CLASSIC;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < getParts().size(); i++) {
			QueryPart part = getParts().get(i);
			if (part.getType().isOperator()) {
				if (part.getType().hasLeftOperand()) {
					builder.append(" ");
				}
				builder.append(part.getContent());
				if (part.getType().hasRightOperand()) {
					builder.append(" ");
				}
			}
			else if (part.getType() == Type.STRING) {
				builder.append("\"" + formatString(part.getContent().toString()) + "\"");
			}
			else if (part.getType().isNative()) {
				builder.append(part.getContent() == null ? "null" : part.getContent().toString());
			}
			else if (part.getType() == Type.OPERATION) {
				// variable operations do not need to be in ()
				if (((Operation<?>) part.getContent()).getType() == OperationType.VARIABLE) {
					builder.append(part.getContent().toString());
				}
				// if it's a classic operation but the operator is at the same level as this one, it is ok
				else if (((Operation<?>) part.getContent()).getType() == OperationType.CLASSIC) {
					int ownPrecedence = -1;
					// get own precedence
					for (QueryPart child : getParts()) {
						if (child.getType().isOperator() && child.getType().getPrecedence() > ownPrecedence) {
							ownPrecedence = child.getType().getPrecedence();
						}
					}
					int otherPrecedence = -1;
					for (QueryPart child : ((Operation<?>) part.getContent()).getParts()) {
						if (child.getType().isOperator() && child.getType().getPrecedence() > otherPrecedence) {
							otherPrecedence = child.getType().getPrecedence();
						}
					}
					if (ownPrecedence != otherPrecedence) {
						builder.append("(" + part.getContent().toString() + ")");	
					}
					else {
						// if the right operand is an operation and it is NOT reverse analyzed, that must mean it has to be evaluated first, add () to it
						if (i > 0 && !PathAnalyzer.reversedEvaluationTypes.contains(otherPrecedence)) {
							builder.append("(" + part.getContent().toString() + ")");	
						}
						else {
							builder.append(part.getContent().toString());
						}
					}
				}
				else {
					builder.append(part.getContent().toString());
				}
			}
		}
		return builder.toString();
	}
}
