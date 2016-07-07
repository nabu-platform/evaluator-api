package be.nabu.libs.evaluator.api.operations;

import be.nabu.libs.evaluator.QueryPart;

public interface OperationExecutor {
	
	public enum Operator {
		AND(QueryPart.Type.BITWISE_AND),
		DIV(QueryPart.Type.DIVIDE),
		MINUS(QueryPart.Type.SUBSTRACT),
		MOD(QueryPart.Type.MOD),
		MULTIPLY(QueryPart.Type.MULTIPLY),
		NEXT(QueryPart.Type.INCREASE),
		OR(QueryPart.Type.BITWISE_OR),
		PLUS(QueryPart.Type.ADD),
		POWER(QueryPart.Type.POWER),
		PREVIOUS(QueryPart.Type.DECREASE),
		XOR(QueryPart.Type.XOR)
		;
		
		private QueryPart.Type queryPart;

		private Operator(QueryPart.Type queryPart) {
			this.queryPart = queryPart;
		}

		public QueryPart.Type getQueryPartType() {
			return queryPart;
		}
	}
	
	public boolean support(Object leftOperand, QueryPart.Type operator, Object rightOperand);
	public Object calculate(Object leftOperand, QueryPart.Type operator, Object rightOperand);
}
