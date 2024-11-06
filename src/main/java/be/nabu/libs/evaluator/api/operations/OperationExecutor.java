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
