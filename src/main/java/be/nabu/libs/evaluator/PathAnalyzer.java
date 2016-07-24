package be.nabu.libs.evaluator;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Analyzer;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;

public class PathAnalyzer<T> implements Analyzer<T> {
	
	public static List<Integer> reversedEvaluationTypes = Arrays.asList(new Integer [] {
		Type.LOGICAL_AND.getPrecedence(),
		Type.LOGICAL_OR.getPrecedence(),
		Type.XOR.getPrecedence(),
		Type.NOT_XOR.getPrecedence()
	});

	private OperationProvider<T> operationProvider;
	
	public PathAnalyzer(OperationProvider<T> operationProvider) {
		this.operationProvider = operationProvider;
	}
	
	public Operation<T> analyze(List<QueryPart> parts) throws ParseException {
		return analyze(Token.fromList(parts));
	}
		
	/**
	 * This will analyze the query parts to generate a map of "uuid" to the actual operation
	 * The root operation is the lowest level operation in the root scope (offset == 0)
	 *  
	 * @param parts
	 * @return The new position
	 * @throws ParseException 
	 */
	private Operation<T> analyze(Token<QueryPart> start) throws ParseException {
		// keeps track of the last operation, this represents the "root" operation for this scope
		Operation<T> last = null;
		
		// do an initial round from beginning to end to resolve variables, methods & nested scopes
		Token<QueryPart> token = start;
		
		// keeps track of where to stop
		// this is set in case a scope_stop is encountered in the initial analysis
		// this means you are evaluating a "sub" scope and should not go all the way from the end for operator analysis
		Token<QueryPart> stop = null;
		
		// go to the end (break if needed)
		while (token != null) {
			Token<QueryPart> insertedBefore = null;
			boolean isStart = token.equals(start);
			
			// stop when a scope closer is encountered
			if (token.getToken().getType() == Type.SCOPE_STOP || token.getToken().getType() == Type.SEPARATOR || token.getToken().getType() == Type.INDEX_STOP) {
				stop = token;
				break;
			}
			
			// if we encounter a scope opener, we need to delegate
			else if (token.getToken().getType() == Type.SCOPE_START) {
				// calculate the resulting operation of the new scope
				Operation<T> childOperation = analyze(token.getNext());
				
				// insert the token before the current scope_opener
				insertedBefore = token.insertBeforeThis(new QueryPart(Type.OPERATION, childOperation));
				
				// remove the scope start
				token = token.remove();
				
				if (childOperation != null) {
					// remove the child operation
					token = token.remove();
				}

				// remove the scope_stop
				token = token.remove();
				
				last = childOperation;
			}
			
			// if we encounter a method, we need to resolve the parameters with operations, resolve the method through reflection
			// and create a wrapper operation that has as first parameter "method" and each next operation is one parameter scope
			else if (token.getToken().getType() == Type.METHOD) {
				// the operation that will hold the method
				Operation<T> methodOperation = operationProvider.newOperation(OperationType.METHOD);
				
				if (token.getToken().getContent() instanceof Operation) {
					methodOperation.add(new QueryPart(Type.OPERATION, token.getToken().getContent()));
				}
				else {
					// add the method itself as first part
					methodOperation.add(token.getToken());
				}
				
				// remove the method token, the token then points to the scope_start that follows the method
				token = token.remove();
				
				// if the next token is a scope-stop, just remove the scope_start
				if (token.getNext().getToken().getType() == Type.SCOPE_STOP)
					token = token.remove();
				
				// otherwise, analyze the parameters, note that the token still points to the scope_start
				else {
					while (token.getToken().getType() != Type.SCOPE_STOP) {
						// analyze the parameter
						Operation<T> parameterOperation = analyze(token.getNext());

						// if the first loop, this removes the (, afterwards it removes the "," you were on
						// it will now point to the next "," or a ")"
						token = token.remove();
						
						// remove the child operation
						token = token.remove();
												
						// add the parameterOperation to the method operation
						methodOperation.add(new QueryPart(Type.OPERATION, parameterOperation));

						// if the token is now null, we are missing an ending scope
						if (token == null) {
							throw new ParseException("Missing end scope token for method call: " + methodOperation, 0);
						}
					}
				}
				// resolve the method
				methodOperation.finish();
				
				// remove the scope closer, it moves to whatever was after it
				token = token.remove();

				// if we have a method operation, add the currently parsed method again to the token list
				if (token != null && token.getToken().getType() == Type.SCOPE_START) {
					token = token.insertBeforeThis(new QueryPart(Type.METHOD, methodOperation));
				}
				else {
					// add operation to the tokens
					if (token == null)
						start.getLast().insertAfterThis(new QueryPart(Type.OPERATION, methodOperation));
					else
						insertedBefore = token.insertBeforeThis(new QueryPart(Type.OPERATION, methodOperation));
				}
				// the variable is followed by a method statement, so the variable itself must return a method
				// set the last
				last = methodOperation;
			}
			
			// if we meet a variable, we need to concatenate all the variable bits into one "operation"
			else if (token.getToken().getType() == Type.VARIABLE) {
				// the operation to hold the variable resolution
				Operation<T> variableOperation = operationProvider.newOperation(OperationType.VARIABLE);
				
				// if this variable declaration was immediately preceeded by a method declaration, you are accessing the result set from there
				// there is no other legal interpretation possible
				// we also check the previous token because apart from the last operation, we need to check that no operators are in between there
				if (last != null && last.getType() == OperationType.METHOD && token.getPrevious().getToken().getType() == QueryPart.Type.OPERATION) {
					variableOperation.add(new QueryPart(Type.OPERATION, last));
					token.getPrevious().remove();
				}
				
				// a variable needs to be added to the operation
				// an index start indicates an indexed access to the variable and is in the same operation
				while (token != null && (token.getToken().getType() == Type.VARIABLE || token.getToken().getType() == Type.INDEX_START)) {
					// if it's a variable part, just add it to the operation and delete it from the tokens
					if (token.getToken().getType() == Type.VARIABLE) {
						// need to split the variable along "/" lines (except leading)
						// suppose you want to access an element in all the items in a list, the usual notation would be "mystructure/myelement"
						// however, structure instance is not intelligent enough to loop over it all, that's what the variable operation is for
						String path = (String) token.getToken().getContent();
						String [] parts = path.split("(?<!^)/");
						for (String part : parts)
							variableOperation.add(new QueryPart(Type.VARIABLE, part));
//						variableOperation.add(token.getToken());
						token = token.remove();
					}
					else {
						// we have an index start, whatever is in between the indexes must be resolved by a separate operation
						Operation<T> indexOperation = analyze(token.getNext());
						
						// the token still points to the index_start, remove it
						token = token.remove();
						
						// remove the child operation
						token = token.remove();
												
						// now it points to the index_stop, remove it
						token = token.remove();

						// add the index operation to the variable operation
						variableOperation.add(new QueryPart(Type.OPERATION, indexOperation));
					}
				}
				
				variableOperation.finish();
				
				// the variable is followed by a method statement, so the variable itself must return a method
				if (token != null && token.getToken().getType() == Type.SCOPE_START) {
					token = token.insertBeforeThis(new QueryPart(Type.METHOD, variableOperation));
					// original copy paste to support methods that are returned from a variable operation, e.g. test/doIt()
					// this can be ignored if we use the recursiveness of the parser by adding this as a method operation again but instead of a name, it has a variable operation as content
//					// the operation that will hold the method
//					Operation<T> methodOperation = operationProvider.newOperation(OperationType.METHOD);
//					
//					// add the method itself as first part
//					methodOperation.add(new QueryPart(Type.VARIABLE, variableOperation));
//
//					// if the next token is a scope-stop, just remove the scope_start
//					if (token.getNext().getToken().getType() == Type.SCOPE_STOP) {
//						token = token.remove();
//					}
//					else {
//						while (token.getToken().getType() != Type.SCOPE_STOP) {
//							// analyze the parameter
//							Operation<T> parameterOperation = analyze(token.getNext());
//
//							// if the first loop, this removes the (, afterwards it removes the "," you were on
//							// it will now point to the next "," or a ")"
//							token = token.remove();
//							
//							// remove the child operation
//							token = token.remove();
//										
//							// add the parameterOperation to the method operation
//							methodOperation.add(new QueryPart(Type.OPERATION, parameterOperation));
//
//							// if the token is now null, we are missing an ending scope
//							if (token == null) {
//								throw new ParseException("Missing end scope token for method call: " + methodOperation, 0);
//							}
//						}
//					}
//					methodOperation.finish();
//					
//					// remove the scope close
//					token = token.remove();
//					
//					// add the variable operation to the tokens
//					if (token == null)
//						start.getLast().insertAfterThis(new QueryPart(Type.OPERATION, methodOperation));
//					else
//						insertedBefore = token.insertBeforeThis(new QueryPart(Type.OPERATION, methodOperation));
//					last = methodOperation;
				}
				else {
					// add the variable operation to the tokens
					if (token == null)
						start.getLast().insertAfterThis(new QueryPart(Type.OPERATION, variableOperation));
					else
						insertedBefore = token.insertBeforeThis(new QueryPart(Type.OPERATION, variableOperation));
					// set the last
					last = variableOperation;
				}
			}
			else
				token = token.getNext();
			
			if (isStart && insertedBefore != null)
				start = insertedBefore;
		}
		
		// loop over the all the tokens, we need to resolve operators by descending precedence
		for (int i = QueryPart.Type.getMaxPrecedence(); i >= 0; i--) {
			// normalize the start, this is done to track removed starts
			Operation<T> newLast = reversedEvaluationTypes.contains(i) ? analyzeResolvedReverse(i, start.normalize(), stop) : analyzeResolvedForward(i, start.normalize(), stop);
			if (newLast != null)
				last = newLast;
		}
		
		return last;
	}
	
	@SuppressWarnings("unchecked")
	private Operation<T> analyzeResolvedReverse(int precedence, Token<QueryPart> start, Token<QueryPart> stop) throws ParseException {
		Token<QueryPart> token = null;
		// if we know of a "stop", use it
		if (stop != null) {
			token = stop.getPrevious();
			// stop
			if (token == null)
				return null;
		}
		// you are at the root scope, always go all the way from the end
		else {
			// stop
			if (start == null)
				return null;

			// always start from the end, operators must be executed left to right
			// so they must be analyzed right to left to nest properly
			token = start.getLast();
		}
		Operation<T> last = null;
		// we need to loop over the parts
		while (token != null) {
			// stop when a scope opener is encountered, you are working in reverse and not everything may be resolved in parent scope
			if (token.getToken().getType() == Type.SCOPE_START || token.getToken().getType() == Type.SEPARATOR || token.getToken().getType() == Type.INDEX_START)
				break;

			// no further back than the start
			else if (start != null && start.hasPrevious() && token.equals(start.getPrevious()))
				break;
			
			// if we encounter an operator, resolve it
			// all other items should either be resolved variables/methods/nested scopes or unresolved native types 
			else if (token.getToken().getType().isOperator()) {
				
				// all higher precedence operators should already be gone from previous parsing rounds
				if (token.getToken().getType().getPrecedence() > precedence)
					throw new ParseException("Found operator " + token.getToken().getContent() + " of precedence " + token.getToken().getType().getPrecedence() + " while parsing for precedence " + precedence, 0);
				
				// if we find a "lower" operator, skip it for now, we'll get back to you buddy, don't worry
				else if (token.getToken().getType().getPrecedence() < precedence) {
					token = token.getPrevious();
					// check comment in other method
					last = null;
				}
				
				// otherwise we just add the part to the current operation
				else {
					// create a new operation
					Operation<T> operation = operationProvider.newOperation(OperationType.CLASSIC);
					
					// if there is a left operand, add it
					if (token.getToken().getType().hasLeftOperand()) {
						operation.add(token.getPrevious().getToken());
						// remove it
						token.getPrevious().remove();
					}
					
					// add the operator itself
					operation.add(token.getToken());
					
					// if you are expecting a right operand, add it
					if (token.getToken().getType().hasRightOperand()) {
						operation.add(token.getNext().getToken());
						// remove it
						token.getNext().remove();
					}
					
					// remove the new operation after the operator
					token.insertAfterThis(new QueryPart(Type.OPERATION, operation));
					
					// remove the current token (the operator)
					token = token.removeInReverse();

					// set last
					last = operation;
				}
			}
			else if (token.getToken().getType() == Type.OPERATION) {
				if (last != null && token.getNext() != null && token.getNext().getToken().getType() == Type.OPERATION && last.equals(token.getNext().getToken().getContent())) {
					throw new ParseException("A dangling token was detected: " + token.getNext().getToken(), 0);
				}
				last = (Operation<T>) token.getToken().getContent();
				token = token.getPrevious();
			}
			// if still unresolved at this point, it must be a native type
			else if (!token.getToken().getType().isNative())
				throw new ParseException("Expecting only operators and native types at this point, found: " + token.getToken(), 0);
			// leave it for the operators
			else {
				if (last != null && last.getType() == OperationType.CLASSIC) {
					throw new ParseException("Unexpected operand after '" + last + "': " + token.getToken().getContent(), 0);
				}
				// assign it to "last" so it gets picked up in case you just print out a native type
				last = operationProvider.newOperation(OperationType.NATIVE);
				last.add(token.getToken());
				token = token.getPrevious();
			}
		}
		return last;
	}
	
	@SuppressWarnings("unchecked")
	private Operation<T> analyzeResolvedForward(int precedence, Token<QueryPart> start, Token<QueryPart> stop) throws ParseException {
		Token<QueryPart> token = null;
		// if we know of a "start", use it
		if (start == null)
			return null;
		token = start;
		Operation<T> last = null;
		// we need to loop over the parts
		while (token != null) {
			if (token.getToken().getType() == Type.SCOPE_STOP || token.getToken().getType() == Type.SEPARATOR || token.getToken().getType() == Type.INDEX_STOP)
				break;
			else if (stop != null && token.equals(stop))
				break;
			else if (token.getToken().getType().isOperator()) {
				
				if (token.getToken().getType().getPrecedence() > precedence)
					throw new ParseException("Found operator " + token.getToken().getContent() + " of precedence " + token.getToken().getType().getPrecedence() + " while parsing for precedence " + precedence, 0);
				
				else if (token.getToken().getType().getPrecedence() < precedence) {
					token = token.getNext();
					// the "last" operation is meant to convey the very last calculation operation generated from the tokens
					// it will contain all the other operations where necessary
					// however if we have detected an operator with a lower precedence, we are definitely not at the "last" operation yet
					// so we reset it to null to allow us to perform the check you see in the last "else" statement of this method
					// this way we can detect wrongly formatted operations
					last = null;
				}
				
				else {
					Operation<T> operation = operationProvider.newOperation(OperationType.CLASSIC);
					
					if (token.getToken().getType().hasLeftOperand()) {
						if (!token.hasPrevious()) {
							throw new ParseException("The operand " + token.getToken().getType() + " expects a left operand but there wasn't one", 0);
						}
						operation.add(token.getPrevious().getToken());
						token.getPrevious().remove();
					}
					
					// add the operator itself
					operation.add(token.getToken());
					
					// if you are expecting a right operand, add it
					if (token.getToken().getType().hasRightOperand()) {
						if (!token.hasNext()) {
							throw new ParseException("The operand " + token.getToken().getType() + " expects a right operand but there wasn't one", 0);
						}
						operation.add(token.getNext().getToken());
						// remove it
						token.getNext().remove();
					}
					
					// remove the new operation after the operator
					token.insertBeforeThis(new QueryPart(Type.OPERATION, operation));
					
					// remove the current token (the operator)
					token = token.remove();

					// set last
					last = operation;
				}
			}
			else if (token.getToken().getType() == Type.OPERATION) {
				if (last != null && token.getPrevious() != null && token.getPrevious().getToken().getType() == Type.OPERATION && last.equals(token.getPrevious().getToken().getContent())) {
					throw new ParseException("A dangling token was detected: " + token.getPrevious().getToken() + " after " + last + " at precedence: " + precedence, 0);
				}
				last = (Operation<T>) token.getToken().getContent();
				token = token.getNext();
			}
			// if still unresolved at this point, it must be a native type
			else if (!token.getToken().getType().isNative())
				throw new ParseException("Expecting only operators and native types at this point, found: " + token.getToken(), 0);
			// leave it for the operators
			else {
				if (last != null && last.getType() == OperationType.CLASSIC) {
					throw new ParseException("Unexpected operand after '" + last + "': " + token.getToken().getContent(), 0);
				}
				// assign it to "last" so it gets picked up in case you just print out a native type
				last = operationProvider.newOperation(OperationType.NATIVE);
				last.add(token.getToken());
				token = token.getNext();
			}
		}
		return last;
	}
}
/**
 * Suppose you have:
 * (a && b) || (c && d) || (d && e)
 * If you analyze this in a "forward" way (making operations as you move forward in the list of tokens), you get:
 * 
(
(
	(
		VARIABLE[a]
	),
	LOGICAL_AND[&&]		(
		VARIABLE[b]
	)
),
LOGICAL_OR[||]	(
	(
		VARIABLE[c]
	),
	LOGICAL_AND[&&]		(
		VARIABLE[d]
	)
)
),
LOGICAL_OR[||](
(
	VARIABLE[e]
),
LOGICAL_AND[&&]	(
	VARIABLE[f]
)
)

 * Note that if you execute this root operation you actually evaluate the "last" or first
 * If you reverse the analysis you get:

(
(
	VARIABLE[a]
),
LOGICAL_AND[&&]	(
	VARIABLE[b]
)
),
LOGICAL_OR[||](
(
	(
		VARIABLE[c]
	),
	LOGICAL_AND[&&]		(
		VARIABLE[d]
	)
),
LOGICAL_OR[||]	(
	(
		VARIABLE[e]
	),
	LOGICAL_AND[&&]		(
		VARIABLE[f]
	)
)
) 
 * However this only works for the "conditional" evaluations, let's suppose we have:
 * a + b - c + d
 * 
 * If you use the same reversed trick as for the above statements you get:

(
VARIABLE[a]
),
ADD[+](
(
	VARIABLE[b]
),
SUBSTRACT[-]	(
	(
		VARIABLE[c]
	),
	ADD[+]		(
		VARIABLE[d]
	)
)
)

 * While at first glance this seems ok, if you actually evaluate this, the most inner substract will be executed first which means you actually do:
 * a + (b - (c + d))
 * 
 * Which is entirely not what you want
 * Forward analysis gives you:
(
(
	(
		VARIABLE[a]
	),
	ADD[+]		(
		VARIABLE[b]
	)
),
SUBSTRACT[-]	(
	VARIABLE[c]
)
),
ADD[+](
VARIABLE[d]
)
 * IMPORTANT: due to the inner operations being executed first the forward analyzed OR statement may still yield the proper result because all nested operators also have delayed execution
 * However I remember when I first designed it, I had only forward analysis but due to a bug with the logical evaluation I switched it to reverse analysis. The actual bug is lost in time though.
 * When reviewing the software a year later I found that reverse analysis does not work with math so I added forward analysis again
 * In the future it may turn out that the reverse analysis is no longer necessary.   
 */
