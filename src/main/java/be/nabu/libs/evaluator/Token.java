package be.nabu.libs.evaluator;

import java.util.List;

/**
 * A token is actually a linked list where you can easily replace objects at runtime
 * @author alex
 *
 */
public class Token<T> {
	
	private Token<T> previous = null, next = null;
	
	private T token;
	
	private boolean removed = false;
	private Token<T> replacement;
	
	public Token(T token) {
		this.token = token;
	}
	
	public T getToken() {
		return token;
	}
	
	public boolean hasNext() {
		return next != null;
	}
	
	public boolean hasPrevious() {
		return previous != null;
	}
	
	public Token<T> getNext() {
		return next;
	}
	
	public Token<T> getPrevious() {
		return previous;
	}
	
	public void setNext(Token<T> next) {
		this.next = next;
	}

	public void setPrevious(Token<T> previous) {
		this.previous = previous;
	}
	
	public Token<T> normalize() {
		return removed ? (replacement == null ? null : replacement.normalize()) : this;
	}
	
	/**
	 * This removes the current token and returns the next token
	 * @return
	 */
	public Token<T> remove() {
		removed = true;
		// rebind the previous & next tokens
		if (hasPrevious())
			getPrevious().setNext(getNext());
		if (hasNext()) {
			replacement = getNext();
			getNext().setPrevious(getPrevious());
		}
		// return the next one
		return getNext();
	}
	
	public Token<T> removeInReverse() {
		removed = true;
		// rebind the previous & next tokens
		if (hasPrevious())
			getPrevious().setNext(getNext());
		if (hasNext()) {
			replacement = getNext();
			getNext().setPrevious(getPrevious());
		}
		// return the previous token
		return getPrevious();
	}
	
	/**
	 * Insert the object after the current token and returns the new token
	 * @param object
	 * @return
	 */
	public Token<T> insertAfterThis(T object) {
		Token<T> token = new Token<T>(object);
		token.setPrevious(this);
		if (hasNext()) {
			token.setNext(getNext());
			getNext().setPrevious(token);
		}
		setNext(token);
		return token;
	}
	
	public Token<T> insertBeforeThis(T object) {
		Token<T> token = new Token<T>(object);
		token.setNext(this);
		if (hasPrevious()) {
			token.setPrevious(getPrevious());
			getPrevious().setNext(token);
		}
		setPrevious(token);
		return token;		
	}
	
	public static <T> Token<T> fromList(List<T> tokens) {
		if (tokens.size() <= 0)
			return null;
		else {
			// create initial
			Token<T> root = new Token<T>(tokens.get(0));
			Token<T> previous = root;
			for (int i = 1; i < tokens.size(); i++) {
				Token<T> next = new Token<T>(tokens.get(i));
				previous.setNext(next);
				next.setPrevious(previous);
				previous = next;
			}
			return root;
		}
	}
	
	public Token<T> getLast() {
		Token<T> last = this;
		while (last.hasNext())
			last = last.getNext();
		return last;
	}
	
	@Override
	public String toString() {
		return toString(null);
	}
	
	public String toString(Token<T> end) {
		String result = token.toString();
		if (hasNext() && (end == null || !getNext().equals(end)))
			result += ", " + getNext();
		return result;
	}
}
