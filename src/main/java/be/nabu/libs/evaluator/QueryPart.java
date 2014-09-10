package be.nabu.libs.evaluator;

public class QueryPart {
	
	public enum Type {
		
		// types that have to be interpreted
		VARIABLE,
		METHOD,
		SCOPE_START,
		SCOPE_STOP,
		INDEX_START,
		INDEX_STOP,
		SEPARATOR,
		OPERATION,
		
		// native types
		STRING(true),
		NUMBER_INTEGER(true),
		NUMBER_DECIMAL(true),
		BOOLEAN_TRUE(true),
		BOOLEAN_FALSE(true),
		NULL(true),
		
		// operators
		LOGICAL_OR(true, 0, true, true),
		LOGICAL_AND(true, 1, true, true),
		BITWISE_OR(true, 2, true, true),
		XOR(true, 3, true, true),
		NOT_XOR(true, 3, true, true),
		BITWISE_AND(true, 4, true, true),
		EQUALS(true, 5, true, true),
		NOT_EQUALS(true, 5, true, true),
		MATCHES(true, 5, true, true),
		NOT_MATCHES(true, 5, true, true),
		IN(true, 5, true, true),
		NOT_IN(true, 5, true, true),
		GREATER_OR_EQUALS(true, 6, true, true),
		GREATER(true, 6, true, true),
		LESSER_OR_EQUALS(true, 6, true, true),
		LESSER(true, 6, true, true),
		ADD(true, 7, true, true),
		SUBSTRACT(true, 7, true, true),
		DIVIDE(true, 8, true, true),
		MULTIPLY(true, 8, true, true),
		MOD(true, 8, true, true),
		POWER(true, 9, true, true),
		INCREASE(true, 10, true, false),
		DECREASE(true, 10, true, false),
		NOT(true, 10, false, true)
		;		
		
		/**
		 * The "isNative" variable indicates whether the content is already a native object or if it needs to be interpreted
		 */
		private boolean isOperator = false, isNative = false, hasLeftOperand = false, hasRightOperand = false;
		
		protected static int maxPrecedence = -1;
		/**
		 * The precedence of the item, the higher it is, the earlier it gets executed
		 */
		private int precedence = -1;
		
		private Type() {
			this(false, false, -1, false, false);
		}
		private Type(boolean isNative) {
			this(false, isNative, -1, false, false);
		}
		private Type(boolean isOperator, int precedence, boolean hasLeftOperand, boolean hasRightOperand) {
			this(isOperator, false, precedence, hasLeftOperand, hasRightOperand);
		}
		private Type(boolean isOperator, boolean isNative, int precedence, boolean hasLeftOperand, boolean hasRightOperand) {
			this.isNative = isNative;
			this.isOperator = isOperator;
			this.precedence = precedence;
			this.hasLeftOperand = hasLeftOperand;
			this.hasRightOperand = hasRightOperand;
		}
		public boolean isOperator() {
			return isOperator;
		}
		public boolean isNative() {
			return isNative;
		}
		public int getPrecedence() {
			return precedence;
		}
		public static int getMaxPrecedence() {
			if (maxPrecedence < 0) {
				for (Type type : values()) {
					if (type.getPrecedence() > maxPrecedence)
						maxPrecedence = type.getPrecedence();
				}
			}
			return maxPrecedence;
		}
		public boolean hasLeftOperand() {
			return hasLeftOperand;
		}
		public boolean hasRightOperand() {
			return hasRightOperand;
		}
	}
	
	private Object content;
	private Type type;

	public QueryPart(Type type, Object content) {
		this.type = type;
		this.content = content;
	}

	public Object getContent() {
		return content;
	}

	public Type getType() {
		return type;
	}
	
	@Override
	public boolean equals(Object object) {
		if (!(object instanceof QueryPart))
			return false;
		else if (!((QueryPart) object).getType().equals(getType()))
			return false;
		else if (getType() == Type.NUMBER_INTEGER)
			return ((Number) getContent()).longValue() == ((Number) ((QueryPart) object).getContent()).longValue();
		else if (getType() == Type.NUMBER_DECIMAL)
			return ((Number) getContent()).doubleValue() == ((Number) ((QueryPart) object).getContent()).doubleValue();
		else
			return getContent().equals(((QueryPart) object).getContent());
	}
	
	@Override
	public String toString() {
		return getType() + " = " + getContent();
	}
	
	public void setContent(Object content) {
		this.content = content;
	}
}
