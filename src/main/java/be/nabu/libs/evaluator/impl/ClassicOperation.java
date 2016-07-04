package be.nabu.libs.evaluator.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

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
import be.nabu.libs.evaluator.api.operations.OperationExecutor;
import be.nabu.libs.evaluator.api.operations.OperationExecutor.Operator;
import be.nabu.libs.evaluator.api.operations.Or;
import be.nabu.libs.evaluator.api.operations.Plus;
import be.nabu.libs.evaluator.api.operations.Power;
import be.nabu.libs.evaluator.api.operations.Previous;
import be.nabu.libs.evaluator.api.operations.Xor;
import be.nabu.libs.evaluator.base.BaseOperation;

public class ClassicOperation<T> extends BaseOperation<T> {
	
	private Converter converter;
	
	private static Map<Class<?>, OperationExecutor> operationExecutors;
	
	@Override
	public void finish() {
		// do nothing
	}

	public static OperationExecutor getOperationExecutor(Class<?> clazz) {
		if (operationExecutors == null) {
			synchronized(ClassicOperation.class) {
				if (operationExecutors == null) {
					Map<Class<?>, OperationExecutor> operationExecutors = new HashMap<Class<?>, OperationExecutor>();
					for (OperationExecutor provider : ServiceLoader.load(OperationExecutor.class)) {
						operationExecutors.put(provider.getSupportedType(), provider);
					}
					ClassicOperation.operationExecutors = operationExecutors;
				}
			}
		}
		// could build out the cache for all classes but this could grow to great proportions
		// the actual provided operation providers will be very small so the for loop is currently considered the better option
		for (Class<?> supportedType : operationExecutors.keySet()) {
			if (supportedType.isAssignableFrom(clazz)) {
				return operationExecutors.get(supportedType);
			}
		}
		return null;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes", "incomplete-switch" })
	@Override
	public Object evaluate(T context) throws EvaluationException {
		try {
			for (int i = 0; i < getParts().size(); i++) {
				QueryPart part = getParts().get(i);
				// only interested in operators
				if (part.getType().isOperator()) {
					// get the operands
					Object left = part.getType().hasLeftOperand() ? getOperand(context, i - 1) : null;
				
					OperationExecutor executor = left == null ? null : getOperationExecutor(left.getClass());
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
							if (executor != null && executor.support(Operator.PLUS)) {
								return executor.calculate(left, Operator.PLUS, right);
							}
							else if (left instanceof Plus) {
								return ((Plus) left).plus(right);
							}
							else if (left == null) {
								// going for string concatenate
								if (right instanceof String) {
									left = "null";
								}
								else {
									throw new NullPointerException("The left operand of an ADD method was null");
								}
							}
							// for strings: if the left is a string and the right can not be converted, use default toString() logic
							if (left instanceof String && right != null && !getConverter().canConvert(right.getClass(), String.class)) {
								right = right.toString();
							}
							else {
								right = getConverter().convert(right, left.getClass());
							}
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).add((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).add((BigDecimal) right);
							}
							break;
						case SUBSTRACT:
							if (executor != null && executor.support(Operator.MINUS)) {
								return executor.calculate(left, Operator.MINUS, right);
							}
							else if (left instanceof Minus) {
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).subtract((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).subtract((BigDecimal) right);
							}
							break;
						case DIVIDE:
							if (executor != null && executor.support(Operator.DIV)) {
								return executor.calculate(left, Operator.DIV, right);
							}
							else if (left instanceof Div) {
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).divide((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).divide((BigDecimal) right);
							}
							break;
						case MOD:
							if (executor != null && executor.support(Operator.MOD)) {
								return executor.calculate(left, Operator.MOD, right);
							}
							else if (left instanceof Mod) {
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).remainder((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).remainder((BigDecimal) right);
							}
							break;
						case MULTIPLY:
							if (executor != null && executor.support(Operator.MULTIPLY)) {
								return executor.calculate(left, Operator.MULTIPLY, right);
							}
							else if (left instanceof Multiply) {
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).multiply((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).multiply((BigDecimal) right);
							}
							break;
						case POWER:
							if (executor != null && executor.support(Operator.POWER)) {
								return executor.calculate(left, Operator.POWER, right);
							}
							else if (left instanceof Power) {
								return ((Power) left).power(right);
							}
							if (left instanceof BigInteger) {
								right = getConverter().convert(right, Integer.class);
								return ((BigInteger) left).pow((Integer) right);
							}
							else if (left instanceof BigDecimal) {
								right = getConverter().convert(right, Integer.class);
								return ((BigDecimal) left).pow((Integer) right);
							}
							else {
								right = getConverter().convert(right, left.getClass());
								return Math.pow(((Number) left).doubleValue(), ((Number) right).doubleValue());
							}
						case BITWISE_AND:
							if (executor != null && executor.support(Operator.AND)) {
								return executor.calculate(left, Operator.AND, right);
							}
							else if (left instanceof And) {
								return ((And) left).and(right);
							}
							return getConverter().convert(left, Boolean.class) & getConverter().convert(right, Boolean.class);
						case BITWISE_OR:
							if (executor != null && executor.support(Operator.OR)) {
								return executor.calculate(left, Operator.OR, right);
							}
							else if (left instanceof Or) {
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
							else if (right instanceof Iterable) {
								for (Object single : (Iterable) right) {
									if (single != null && single.equals(left)) {
										return true;
									}
								}
								return false;
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
							else if (right instanceof Iterable) {
								for (Object single : (Iterable) right) {
									if (single != null && single.equals(left)) {
										return false;
									}
								}
								return true;
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
							if (executor != null && executor.support(Operator.XOR)) {
								Boolean result = (Boolean) executor.calculate(left, Operator.XOR, right);
								return !result;
							}
							else if (left instanceof Xor) {
								Boolean result = (Boolean) ((Xor) left).xor(right);
								return !result;
							}
							left = getConverter().convert(left, Boolean.class);
							right = getConverter().convert(right, Boolean.class);
							return (Boolean) left.equals((Boolean) right);
						case XOR:
							if (executor != null && executor.support(Operator.XOR)) {
								return executor.calculate(left, Operator.XOR, right);
							}
							else if (left instanceof Xor) {
								return ((Xor) left).xor(right);
							}
							left = getConverter().convert(left, Boolean.class);
							right = getConverter().convert(right, Boolean.class);
							return !(Boolean) left.equals((Boolean) right);
						case INCREASE:
							if (executor != null && executor.support(Operator.NEXT)) {
								return executor.calculate(left, Operator.NEXT, right);
							}
							else if (left instanceof Next) {
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).add(BigInteger.ONE);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).add(BigDecimal.ONE);
							}
						case DECREASE:
							if (executor != null && executor.support(Operator.PREVIOUS)) {
								return executor.calculate(left, Operator.PREVIOUS, right);
							}
							else if (left instanceof Previous) {
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).subtract(BigInteger.ONE);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).subtract(BigDecimal.ONE);
							}
					}
				}
			}
		}
		catch (Exception e) {
			throw new EvaluationException("Could not perform operation: " + toString(), e);
		}
		throw new EvaluationException("Could not perform operation: " + toString());
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
					boolean isNamed = false;
					for (QueryPart child : getParts()) {
						if (child.getType().isOperator() && child.getType() == Type.NAMING) {
							isNamed = true;
						}
						if (child.getType().isOperator() && child.getType().getPrecedence() > ownPrecedence) {
							ownPrecedence = child.getType().getPrecedence();
						}
					}
					int otherPrecedence = -1;
					for (QueryPart child : ((Operation<?>) part.getContent()).getParts()) {
						if (child.getType().isOperator() && child.getType() == Type.NAMING) {
							isNamed = true;
						}
						if (child.getType().isOperator() && child.getType().getPrecedence() > otherPrecedence) {
							otherPrecedence = child.getType().getPrecedence();
						}
					}
					if (!isNamed && ownPrecedence != otherPrecedence) {
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
