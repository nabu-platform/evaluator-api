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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

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
	private static Boolean alwaysUseDoubles = Boolean.parseBoolean(System.getProperty("math.always.doubles", "false"));
	
	public ClassicOperation() {
		// auto construct
	}
	public ClassicOperation(boolean allowOperatorOverloading) {
		this.allowOperatorOverloading = allowOperatorOverloading;
	}
	
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

	public static Object normalize(Object value) {
		return value instanceof BigDecimal ? ((BigDecimal) value).stripTrailingZeros() : value;
	}
	
	private boolean allowOperatorOverloading = true;
	
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

	// we don't want to normalize string concatenation (yet?) because it is too unpredictable
	// do we allow strings to be used as numbers/booleans etc if they contain the right values? always? never?
	// when you enable the double stuff though we assume you want to be smarter about number casting
	private Object normalizeLeft(Object left, Object right) {
		if (!alwaysUseDoubles || !(left instanceof Number)) {
			return left;
		}
		if (right instanceof BigDecimal && !(left instanceof BigDecimal)) {
			return getConverter().convert(left, BigDecimal.class);
		}
		else if (right instanceof BigInteger && !(left instanceof BigInteger) && !(left instanceof BigDecimal)) {
			return getConverter().convert(left, BigInteger.class);
		}
		else if (right instanceof Double && !(left instanceof BigInteger) && !(left instanceof BigDecimal) && !(left instanceof Double)) {
			return getConverter().convert(left, Double.class);
		}
		// eg the left is a short or something
		else if (right instanceof Long && !(left instanceof BigInteger) && !(left instanceof BigDecimal) && !(left instanceof Double) && !(left instanceof Long)) {
			return getConverter().convert(left, Long.class);
		}
		return left;
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
							// if the left is null, we set it to false
							if (left == null) {
								return false;
							}
							// if we can convert it to a boolean and it is false, we don't execute the right hand side
							else {
								Boolean newLeft = getConverter().convert(left, Boolean.class);
								if (newLeft != null && !newLeft) {
									return false;
								}
							}
						break;
						case LOGICAL_OR:
							// if the left is not null, it has a chance of being true in which case we don't have to check the right operand
							if (left != null) {
								Boolean newLeft = getConverter().convert(left, Boolean.class);
								// if it can not be converted to boolean, we assume true (because not null), otherwise if the boolean is true we don't want to execute the right hand side
								if (newLeft == null || newLeft) {
									return true;
								}
							}
						break;
					}
					
					Object right = part.getType().hasRightOperand() ? getOperand(context, i + 1, false) : null;
					
					if (allowOperatorOverloading) {
						for (OperationExecutor possibleExecutor : getOperationExecutors()) {
							if (possibleExecutor.support(left, part.getType(), right)) {
								return possibleExecutor.calculate(left, part.getType(), right);
							}
						}
					}
					
					// normalize the value
					// the main problem currently is "bigdecimal" which has an "equals" implementation that does not match the "compareTo"
					// if necessary in the future we could add a "===" operator to do an exact equals (?)
					// https://www.baeldung.com/java-bigdecimal-equals-compareto-difference#:~:text=For%20BigDecimal.,equal%20in%20value%20and%20scale.
					left = normalize(left);
					right = normalize(right);
					
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
							// @2022-01-07
							// if the left is a number and we always want to use doubles, cast it
							// this prevents things like integer division which is really annoying to deal with at every turn
							// or doubles getting cast to integer and losing information simply because the left operand is an integer
							left = normalizeLeft(left, right);
							
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
							left = normalizeLeft(left, right);
							
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
							left = normalizeLeft(left, right);
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
							left = normalizeLeft(left, right);
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
							left = normalizeLeft(left, right);
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
								left = normalizeLeft(left, right);
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
							if (left instanceof Or) {
								return ((Or) left).or(right);
							}
							if (left == null) {
								left = false;
							}
							if (right == null) {
								right = false;
							}
							right = getConverter().convert(right, left.getClass());
							return getConverter().convert(left, Boolean.class) | getConverter().convert(right, Boolean.class);
						case LOGICAL_AND:
							if (left instanceof And) {
								return ((And) left).and(right);
							}
							if (left == null) {
								left = false;
							}
							else {
								Boolean newLeft = getConverter().convert(left, Boolean.class);
								// the left value is not null, if we can't convert it to a boolean, we set it to true (==  not null)
								left = newLeft != null ? newLeft : true;
							}
							if (right == null) {
								right = false;
							}
							else {
								Boolean newRight = getConverter().convert(right, Boolean.class);
								// the left value is not null, if we can't convert it to a boolean, we set it to true (==  not null)
								right = newRight != null ? newRight : true;
							}
							return (Boolean) left && (Boolean) right;
						case LOGICAL_OR:
							if (left instanceof Or) {
								return ((Or) left).or(right);
							}
							if (left == null) {
								left = false;
							}
							else {
								Boolean newLeft = getConverter().convert(left, Boolean.class);
								// the left value is not null, if we can't convert it to a boolean, we set it to true (==  not null)
								left = newLeft != null ? newLeft : true;
							}
							if (right == null) {
								right = false;
							}
							else {
								Boolean newRight = getConverter().convert(right, Boolean.class);
								// the left value is not null, if we can't convert it to a boolean, we set it to true (==  not null)
								right = newRight != null ? newRight : true;
							}
							return (Boolean) left || (Boolean) right;
						case EQUALS:
							if (left == null) {
								return right == null ? true : false;
							}
							else if (right == null) {
								return false;
							}
							else {
								// @2024-02-06
								// suppose you want to compare a UUID to a string that contains a non-UUID value, there will be a conversion path but it will fail to actually convert the value
								// the exception is not always cleanly a classcastexception (check StringToUUID for out of bounds or illegal argument exceptions) so we just catch _all_ exceptions.
								// TODO: we probably need to apply this to some others as well, for example the IN also casts each element in the list to whatever the type is you are comparing it to
								try {
									right = getConverter().convert(right, left.getClass());
								}
								catch (Exception e) {
									return false;
								}
								// the bigdecimal equals() method is _not_ in sync with the compareTo
								// the compareTo strongly recommends keeping these two in sync but does not mandate it
								// this appears to be one of the edge cases
								// bigdecimal takes precision into account with an equals, not so with a compare
								// so when equals 2.0 is not the same as 2.00 but compareto does return 0
								// we are only interested in sane definitions...
								if (left instanceof BigDecimal && right instanceof BigDecimal) {
									return ((BigDecimal) left).compareTo((BigDecimal) right) == 0;
								}
								else if (left instanceof java.util.Date && right instanceof java.util.Date) {
									return ((java.util.Date) left).getTime() == ((java.util.Date) right).getTime(); 
								}
								else if (left instanceof Double && right instanceof Double) {
									return compareDouble(context, i, (Double) left, (Double) right) == 0;
								}
								else if (left instanceof Float && right instanceof Float) {
									return compareFloat(context, (Float) left, (Float) right) == 0;
								}
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
								// @2024-02-06: check equals
								try {
									right = getConverter().convert(right, left.getClass());
								}
								catch (Exception e) {
									return false;
								}
								// the bigdecimal equals() method is _not_ in sync with the compareTo
								// the compareTo strongly recommends keeping these two in sync but does not mandate it
								// this appears to be one of the edge cases
								// bigdecimal takes precision into account with an equals, not so with a compare
								// so when equals 2.0 is not the same as 2.00 but compareto does return 0
								// we are only interested in sane definitions...
								if (left instanceof BigDecimal && right instanceof BigDecimal) {
									return ((BigDecimal) left).compareTo((BigDecimal) right) != 0;
								}
								return !left.equals(right);
							}
						case GREATER:
							if (left == null || right == null) {
								return false;
							}
							right = getConverter().convert(right, left.getClass());
							return ((Comparable) left).compareTo((Comparable) right) > 0;
						case GREATER_OR_EQUALS:
							if (left == null || right == null) {
								return false;
							}
							right = getConverter().convert(right, left.getClass());
							return ((Comparable) left).compareTo((Comparable) right) >= 0;
						case LESSER:
							if (left == null || right == null) {
								return false;
							}
							right = getConverter().convert(right, left.getClass());
							return ((Comparable) left).compareTo((Comparable) right) < 0;
						case LESSER_OR_EQUALS:
							if (left == null || right == null) {
								return false;
							}
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
									if (left == null && single == null) {
										return true;
									}
									else if (left == null) {
										continue;
									}
									// for lazily resolved series
									else if (single instanceof Callable) {
										Object singleResult = ((Callable) single).call();
										// @2024-02-06: check comments in the EQUALS
										// we have the same here: each iteration of the list will be cast to whatever is on the left side, this may not be compatible even if there is a conversion path
										try {
											singleResult = getConverter().convert(singleResult, left.getClass());
										}
										catch (Exception e) {
											continue;
										}
										if (left.equals(singleResult)) {
											return true;
										}
									}
									else if (single != null) {
										// @2024-02-06: check comments above
										try {
											single = getConverter().convert(single, left.getClass());
										}
										catch (Exception e) {
											continue;
										}
										if (left.equals(single)) {
											return true;
										}
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
									if (left == null && single == null) {
										return false;
									}
									else if (left == null) {
										continue;
									}
									// for lazily resolved series
									else if (single instanceof Callable) {
										Object singleResult = ((Callable) single).call();
										// @2024-02-06: check comments in the IN
										try {
											singleResult = getConverter().convert(singleResult, left.getClass());
										}
										catch (Exception e) {
											continue;
										}
										if (left.equals(singleResult)) {
											return false;
										}
									}
									else if (single != null) {
										// @2024-02-06: check comments in the IN
										try {
											single = getConverter().convert(single, left.getClass());
										}
										catch (Exception e) {
											continue;
										}
										if (left.equals(single)) {
											return false;
										}
									}
								}
								return true;
							}
							else {
								List<?> list2 = right instanceof Collection ? new ArrayList((List<?>) right) : Arrays.asList((Object[]) right);
								return !list2.contains(left);
							}
						case NOT:
							// if there is no right, we consider it false and the inverse true
							if (right == null) {
								return true;
							}
							Boolean newRight = getConverter().convert(right, Boolean.class);
							// if we can't transform the right operand to a boolean and it is not null (see above), we return false
							if (newRight == null) {
								return false;
							}
							else {
								return !newRight;
							}
						case MATCHES:
							if (left == null) {
								return false;
							}
							left = getConverter().convert(left, String.class);
							right = getConverter().convert(right, String.class);
							return ((String) left).matches((String) right);
						case NOT_MATCHES:
							if (left == null) {
								return true;
							}
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
	
	// https://en.wikipedia.org/wiki/Machine_epsilon
	private int compareFloat(T context, float left, float right) {
		float epsilon = (float) 1e-6;
		float relativeDeviation = Math.max(left, right) * epsilon;
		float absoluteDeviation = (float) 1e-11;
		float result = Math.abs(left - right);
		if (result <= relativeDeviation || result <= absoluteDeviation) {
			return 0;
		}
		else if (left > right) {
			return 1;
		}
		else {
			return -1;
		}
	}

	private int compareDouble(T context, int position, double left, double right) {
		double absoluteDeviation = (float) 1e-20;
		double relativeDeviation = getDoubleEpsilon(context, position - 1, position + 1, left, right);
		double result = Math.abs(left - right);
		if (result <= relativeDeviation || result <= absoluteDeviation) {
			return 0;
		}
		else if (left > right) {
			return 1;
		}
		else {
			return -1;
		}
	}
	
	protected double getDoubleEpsilon(T context, int leftPosition, int rightPosition, double left, double right) {
		double epsilon = 1e-15;
		double relativeDeviation = Math.max(left, right) * epsilon;
		return relativeDeviation;
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
