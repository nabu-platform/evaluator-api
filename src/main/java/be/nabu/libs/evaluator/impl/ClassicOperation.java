package be.nabu.libs.evaluator.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
import be.nabu.libs.evaluator.api.operations.Or;
import be.nabu.libs.evaluator.api.operations.Plus;
import be.nabu.libs.evaluator.api.operations.Power;
import be.nabu.libs.evaluator.api.operations.Previous;
import be.nabu.libs.evaluator.api.operations.Xor;
import be.nabu.libs.evaluator.base.BaseOperation;

public class ClassicOperation<T> extends BaseOperation<T> {
	
	private static ThreadLocal<MathContext> mathContext = new ThreadLocal<MathContext>();
	
	public static void setMathContext(MathContext context) {
		mathContext.set(context);
	}
	
	public static MathContext getMathContext() {
		return mathContext.get() == null ? MathContext.DECIMAL128 : mathContext.get();
	}
	
	private Converter converter;
	
	private static List<OperationExecutor> operationExecutors;
	
	@Override
	public void finish() {
		// do nothing
	}

	public static List<OperationExecutor> getOperationExecutors() {
		if (operationExecutors == null) {
			synchronized(ClassicOperation.class) {
				if (operationExecutors == null) {
					List<OperationExecutor> operationExecutors = new ArrayList<OperationExecutor>();
					for (OperationExecutor provider : ServiceLoader.load(OperationExecutor.class)) {
						operationExecutors.add(provider);
					}
					ClassicOperation.operationExecutors = operationExecutors;
				}
			}
		}
		return operationExecutors;
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
					Object left = part.getType().hasLeftOperand() ? getOperand(context, i - 1, part.getType() == Type.SUBSTRACT) : null;
				
					// don't get (and potentially evaluate) the right part if it's not necessary
					switch (part.getType()) {
						case LOGICAL_AND:
							if (left == null || !getConverter().convert(left, Boolean.class))
								return false;
						break;
						case LOGICAL_OR:
							if (left != null && getConverter().convert(left, Boolean.class))
								return true;
						break;
					}
					
					Object right = part.getType().hasRightOperand() ? getOperand(context, i + 1, false) : null;

					for (OperationExecutor possibleExecutor : getOperationExecutors()) {
						if (possibleExecutor.support(left, part.getType(), right)) {
							return possibleExecutor.calculate(left, part.getType(), right);
						}
					}
					switch (part.getType()) {
						case ADD:
							if (left instanceof Plus) {
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
							if (left instanceof Minus) {
								return ((Minus) left).minus(right);
							}
							// if there is no left operand, we simply do 0-right
							if (left == null) {
								left = Long.valueOf(0);
								if (right instanceof Number) {
									left = getConverter().convert(left, right.getClass());
								}
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).divide((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								// without a math context things like 4 / 24 can throw arithmetic exceptions as they are infinite numbers: 0.16666666666666
								// you have to choose _some_ precision for the rounding
								return ((BigDecimal) left).divide((BigDecimal) right, getMathContext());
							}
							break;
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).remainder((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).remainder((BigDecimal) right);
							}
							break;
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).multiply((BigInteger) right);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).multiply((BigDecimal) right);
							}
							break;
						case POWER:
							if (left instanceof Power) {
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
								Object result = Math.pow(((Number) left).doubleValue(), ((Number) right).doubleValue());
								return getConverter().convert(result, left.getClass());
							}
						case BITWISE_AND:
							if (left == null) {
								left = false;
							}
							if (right == null) {
								right = false;
							}
							if (left instanceof And) {
								return ((And) left).and(right);
							}
							return getConverter().convert(left, Boolean.class) & getConverter().convert(right, Boolean.class);
						case BITWISE_OR:
							if (left == null) {
								left = false;
							}
							if (right == null) {
								right = false;
							}
							if (left instanceof Or) {
								return ((Or) left).or(right);
							}
							right = getConverter().convert(right, left.getClass());
							return getConverter().convert(left, Boolean.class) | getConverter().convert(right, Boolean.class);
						case LOGICAL_AND:
							if (left == null) {
								left = false;
							}
							if (right == null) {
								right = false;
							}
							return getConverter().convert(left, Boolean.class) && getConverter().convert(right, Boolean.class);
						case LOGICAL_OR:
							if (left == null) {
								left = false;
							}
							if (right == null) {
								right = false;
							}
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
							return ((Comparable) left).compareTo((Comparable) right) > 0;
						case GREATER_OR_EQUALS:
							right = getConverter().convert(right, left.getClass());
							return ((Comparable) left).compareTo((Comparable) right) >= 0;
						case LESSER:
							right = getConverter().convert(right, left.getClass());
							return ((Comparable) left).compareTo((Comparable) right) < 0;
						case LESSER_OR_EQUALS:
							right = getConverter().convert(right, left.getClass());
							return ((Comparable) left).compareTo((Comparable) right) <= 0;
						case IN:
							// if there is no right one, the left can never be "in" it
							if (right == null) {
								return false;
							}
							else if (right instanceof String) {
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
							// if there is no right one, the left is never "in" it
							if (right == null) {
								return true;
							}
							else if (right instanceof String) {
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
							if (left instanceof Xor) {
								Boolean result = (Boolean) ((Xor) left).xor(right);
								return !result;
							}
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
							else if (left instanceof BigInteger) {
								return ((BigInteger) left).add(BigInteger.ONE);
							}
							else if (left instanceof BigDecimal) {
								return ((BigDecimal) left).add(BigDecimal.ONE);
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
	protected Object getOperand(T context, int position, boolean optional) throws EvaluationException {
		QueryPart part = getParts().get(position);
		if (part.getType().isNative()) {
			return part.getContent();
		}
		else if (part.getType() == QueryPart.Type.OPERATION) {
			return ((Operation<T>) part.getContent()).evaluate(context);
		}
		else if (!optional) {
			throw new EvaluationException("Expecting either a native part or an operation");
		}
		else {
			return null;
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
