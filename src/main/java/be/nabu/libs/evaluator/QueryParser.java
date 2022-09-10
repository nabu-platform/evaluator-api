package be.nabu.libs.evaluator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.libs.evaluator.QueryPart.Type;

/**
 * Note: the worded operators are actually non functional atm
 * This is because they will be picked up as variables
 * 
 * I don't want to unnecessarily restrict the naming in structures to fit operators (e.g. how to parse mod/my/structure?)
 * And i've already diverged enough from the xpath standard to not implement them after all
 * Additionally note that the forward slash is used to separate variable parts and as a division operator
 * Parsing variables after the fact will have a lot of divisions...
 * 
 * I "could" replace all "[\s]+mod[\s]+" with "%" to make it easier to parse
 * But this would still not allow you to use "mod" as an actual variable name
 * 
 * The named operators are still in the regexes though, but will never be picked up (var wins)
 * 
 * @author alex
 *
 */
public class QueryParser {
	
	private static QueryParser parser;
	
	private boolean allowUnscopedSeparators = false;
	
	public static QueryParser getInstance() {
		if (parser == null)
			parser = new QueryParser();
		return parser;
	}
	
	/**
	 * Anything that is parsed is cached in the assumption that reparsing leads to the same result
	 * This may not be true if you have slightly different parser settings but should hold for most usecases
	 * If this becomes a problem, add a "context" or something
	 * The speedup of not having to parse every time is considerable though
	 */
	private Map<String, List<QueryPart>> parsed = new HashMap<String, List<QueryPart>>();
	
	/**
	 * Keeps track of the parts (in order!) and their respective regexes
	 */
	private Map<Type, String> parts = new LinkedHashMap<Type, String>();
	
	/**
	 * Identifying regexes can be slightly different from the parsing ones
	 */
	private Map<Type, String> identifier = new HashMap<Type, String>();
	
	/**
	 * Keeps track of any post-formatting you want to apply to certain parts, for example strip the quotes from a string
	 * Basically the result of replaceAll($regex, "$1") is put in place of the actual result
	 */
	private Map<Type, List<String>> post = new HashMap<Type, List<String>>();
	
	protected QueryParser() {
		// TODO: need to update the regex so "\\" is a valid string with a \ in it
		parts.put(Type.STRING, "((?:(?<!(?<!\\\\)\\\\)\".*?(?<!(?<!\\\\)\\\\)\")|(?:(?<!(?<!\\\\)\\\\)'.*?(?<!(?<!\\\\)\\\\)'))");
		parts.put(Type.NUMBER_DECIMAL, "\\b[0-9]+\\.[0-9]+(b|)\\b");
		parts.put(Type.NUMBER_INTEGER, "\\b[0-9]+(b|)\\b");
		parts.put(Type.BOOLEAN_TRUE, "\\btrue\\b");
		parts.put(Type.BOOLEAN_FALSE, "\\bfalse\\b");
		parts.put(Type.NULL, "\\bnull\\b");
		// a method must be followed by an opening scope and must start and end with a \w
		parts.put(Type.METHOD, "([$]+|\\b[a-zA-Z]+)[\\w.]*[\\w]*(?=[\\s]*\\()");
		// each "part" of the variable can start with a "@" or a "$" where "@" is for attribute and "$" is for an internal variable
		// each variable name MUST begin with a character
		// dots are allowed in the variable name because for method namespaces, they must be followed by a "("
		parts.put(Type.VARIABLE, "((/|)(@|)(?:(?:\\b[a-zA-Z_]+|\\$)[\\w.]*|\\.\\.))+\\b");
		parts.put(Type.SEPARATOR, ",");
		parts.put(Type.SCOPE_START, "\\(");
		parts.put(Type.SCOPE_STOP, "\\)");
		parts.put(Type.INDEX_START, "\\[");
		parts.put(Type.INDEX_STOP, "\\]");
		// operators
		parts.put(Type.NAMING, ":");
		parts.put(Type.LOGICAL_AND, "\\band\\b|&&");
		parts.put(Type.LOGICAL_OR, "\\bor\\b|\\|\\|");
		parts.put(Type.BITWISE_OR, "\\|");
		parts.put(Type.BITWISE_AND, "&");
		parts.put(Type.POWER, "\\*\\*");
		parts.put(Type.MULTIPLY, "\\*");
		parts.put(Type.INCREASE, "\\+\\+");
		parts.put(Type.DECREASE, "--");
		parts.put(Type.ADD, "\\+");
		parts.put(Type.SUBSTRACT, "-");
		// in linux type "ctrl+shift+u", this adds an underlined u to the screen, type the code 00f7<enter> which will turn into the division sign. you can also just enter f7<enter>
		parts.put(Type.DIVIDE, "/|÷|\\bdiv\\b");
		parts.put(Type.NOT_IN, "!#|\\bnot in\\b");
		parts.put(Type.IN, "#|\\bin\\b");
		parts.put(Type.GREATER_OR_EQUALS, ">=");
		parts.put(Type.GREATER, ">");
		parts.put(Type.LESSER_OR_EQUALS, "<=");
		parts.put(Type.LESSER, "<");
		parts.put(Type.NOT_MATCHES, "!~");
		parts.put(Type.MATCHES, "~");
		parts.put(Type.MOD, "%|\\bmod\\b");
		parts.put(Type.NOT_XOR, "!\\^");
		parts.put(Type.XOR, "\\^");
		parts.put(Type.NOT_EQUALS, "!=");
		parts.put(Type.EQUALS, "==|=");
		parts.put(Type.NOT, "!");
		parts.put(Type.COMPOSE, "°");
		
		post.put(Type.STRING, Arrays.asList("(?s)^(?:\"|')(.*)(?:\"|')"));
		// the lookahead for a scope opener is currently hardcoded!!!
		identifier.put(Type.METHOD, "([$]+|\\b[a-zA-Z]+)[\\w.]*[\\w]*");
	}
	
	public String getRegex() {
		String regex = null;
		for (Type type : parts.keySet()) {
			if (regex == null)
				regex = "";
			else
				regex += "|";
			regex += parts.get(type);
		}
		return "(?s)(?i)(" + regex + ")";
	}
	
	protected Map<Type, String> getParts() {
		return parts;
	}

	protected void setParts(Map<Type, String> parts) {
		this.parts = parts;
	}

	public boolean isLenient() {
		return lenient;
	}

	public void setLenient(boolean lenient) {
		this.lenient = lenient;
	}

	protected Map<Type, List<String>> getPostFormatting() {
		return post;
	}

	protected void setPostFormatting(Map<Type, List<String>> post) {
		this.post = post;
	}

	protected Map<Type, String> getIdentifier() {
		return identifier;
	}

	protected void setIdentifier(Map<Type, String> identifier) {
		this.identifier = identifier;
	}

	/**
	 * If lenient is set to "true", characters that are not part of any regex are simply ignored
	 * Otherwise if set to "false", an error is thrown if the rule contains incorrect characters
	 */
	private boolean lenient = false;
		
	public List<QueryPart> parse(String query) throws ParseException {
		if (!parsed.containsKey(query)) {
			synchronized(parsed) {
				if (!parsed.containsKey(query)) {
					List<QueryPart> tokens = interpret(tokenize(query), false);
					validate(tokens);
					parsed.put(query, tokens);
				}
			}
		}
		return parsed.get(query);
	}
	
	/**
	 * Validates the scopes
	 * @param tokens
	 */
	@SuppressWarnings("incomplete-switch")
	protected void validate(List<QueryPart> tokens) throws ParseException {
		// manipulated upon scope changes
		int scope = 0;
		// manipulated upon index changes
		int index = 0;
		
		for (QueryPart token : tokens) {
			switch(token.getType()) {
				case SCOPE_START: scope++; break;
				case SCOPE_STOP: scope--; break;
				case INDEX_START: index++; break;
				case INDEX_STOP: index--; break;
				case SEPARATOR:
					if (scope <= 0 && !allowUnscopedSeparators)
						throw new ParseException("All separators must exist in a scope", 0);
				break;
			}
		}		
		if (scope > 0)
			throw new ParseException("There are " + scope + " unclosed scopes", 0);
		else if (scope < 0)
			throw new ParseException("There are " + Math.abs(scope) + " scopes that are closed but were never opened to begin with", 0);
		if (index > 0)
			throw new ParseException("There are " + index + " unclosed indexes", 0);
		else if (index < 0)
			throw new ParseException("There are " + Math.abs(index) + " indexes that are closed but were never opened to begin with", 0);
	}
	
	/**
	 * Tokenizes the query based on the regex and enforces the lenient if necessary
	 */
	public List<StringToken> tokenize(String query) throws ParseException {
		String regex = getRegex();
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(query);
		List<StringToken> parts = new ArrayList<StringToken>();
		// keeps track of last parsed position, this is for "lenient" parsing
		int last = -1;
		while (matcher.find()) {
			String preamble = null;
			if (matcher.start() > last + 1) {
				preamble = query.substring(last + 1, matcher.start());	
			}
			if (!lenient && preamble != null && preamble.trim().length() > 0) {
				throw new ParseException("Invalid token detected in [" + (last + 1) + ", " + matcher.start() + "]: '" + preamble + "' of '" + query + "'", last + 1);
			}
			parts.add(new StringToken(matcher.group(), preamble, matcher.start(), matcher.end()));
			last = matcher.end() - 1;
		}
		if (parts.size() == 0) {
			throw new ParseException("The query contains no identifiable tokens", 0);
		}
		return parts;
	}
	
	/**
	 * The tokens are interpreted so the QueryPart can be made
	 * @param tokens
	 * @param start
	 * @return
	 * @throws RuleException 
	 * @throws ParseException 
	 */
	public List<QueryPart> interpret(List<StringToken> tokens, boolean lenient) throws ParseException {
		List<QueryPart> result = new ArrayList<QueryPart>();
		
		for (int i = 0; i < tokens.size(); i++) {
			String token = tokens.get(i).getContent();
			boolean identified = false;
			for (Type type : parts.keySet()) {
				// this is the one
				if (token.matches("(?s)" + (identifier.containsKey(type) ? identifier.get(type) : parts.get(type)))) {
					// hardcoded check for method: need scope opener as next!
					if (type == Type.METHOD && (i == tokens.size() - 1 || !tokens.get(i + 1).getContent().matches(identifier.containsKey(Type.SCOPE_START) ? identifier.get(Type.SCOPE_START) : parts.get(Type.SCOPE_START))))
						continue;
					identified = true;
					// post process if necessary
					if (post.containsKey(type)) {
						String quoteUsed = token.substring(0, 1);
						for (String replace : post.get(type)) {
							token = token.replaceAll(replace, "$1");
						}
						if (type == Type.STRING) {
							token = token.replaceAll("(?<!\\\\)\\\\" + quoteUsed, quoteUsed).replaceAll("(?<!\\\\)\\\\t", "\t").replaceAll("(?<!\\\\)\\\\n", "\n").replaceAll("(?<!\\\\)\\\\r", "\r").replaceAll("\\\\t", "\\t").replaceAll("\\\\n", "\\n").replaceAll("\\\\r", "\\r");
							token = token.replace("\\\\", "\\");
						}
					}
					// parse it as a long
					if (type == Type.NUMBER_INTEGER || type == Type.NUMBER_DECIMAL) {
						QueryPart bumped = null;
						// check if it's a negative number
						if (result.size() >= 1 && result.get(result.size() - 1).getType() == Type.SUBSTRACT) {
							// if there is nothing before the subtract, it is definitely linked to the number
							boolean isSign = result.size() <= 1;
							if (!isSign) {
								// otherwise we check the one before the subtract
								Type previousType = result.get(result.size() - 2).getType();
								// if it's another operator, the subtract is actually a negative sign
								isSign |= previousType.isOperator()
									// or if the type is something that can _not_ be subtracted, it is also a sign
									|| Arrays.asList(new Type [] { Type.SCOPE_START, Type.SEPARATOR, Type.INDEX_START }).contains(previousType);
							}
							if (isSign) {
								token = "-" + token;
								// remove the subtract from the tokens
								bumped = result.remove(result.size() - 1);
							}
						}
						// this is a variable added later on because of a very nasty bug: if we interpret the "-" as a sign, we threw away the token alltogether
						// everything works because we update the number to be negative
						// the only thing that breaks is the string token inside the query token, it still contains the original content of the number, without the leading sign
						// so writing it out based on the string tokens would get you "1" instead of the original "-1"
						// to fix this we create a new token that encompasses both existing tokens
						StringToken tokenToUse = bumped == null ? tokens.get(i) : new StringToken(
							bumped.getToken().getContent() + (tokens.get(i).getPreamble() == null ? "" : tokens.get(i).getPreamble()) + tokens.get(i).getContent(), 
							bumped.getToken().getPreamble(), bumped.getToken().getStart(), tokens.get(i).getEnd());
						if (type == Type.NUMBER_INTEGER) {
							if (token.endsWith("b")) {
								result.add(new QueryPart(tokenToUse, type, new BigInteger(token.substring(0, token.length() - 1))));
							}
							else {
								Long longValue = new Long(token);
								if (longValue > Integer.MAX_VALUE || longValue < Integer.MIN_VALUE) {
									result.add(new QueryPart(tokenToUse, type, longValue));
								}
								else {
									result.add(new QueryPart(tokenToUse, type, new Integer(longValue.intValue())));
								}
							}
						}
						else {
							if (token.endsWith("b")) {
								result.add(new QueryPart(tokenToUse, type, new BigDecimal(token.substring(0, token.length() - 1))));	
							}
							else {
								result.add(new QueryPart(tokenToUse, type, new Double(token)));
							}
						}
					}
					else if (type == Type.BOOLEAN_FALSE)
						result.add(new QueryPart(tokens.get(i), type, false));
					else if (type == Type.BOOLEAN_TRUE)
						result.add(new QueryPart(tokens.get(i), type, true));
					else if (type == Type.NULL)
						result.add(new QueryPart(tokens.get(i), type, null));
					else
						result.add(new QueryPart(tokens.get(i), type, token));
					break;
				}
			}
			if (!identified) {
				if (lenient) {
					result.add(new QueryPart(tokens.get(i), Type.UNKNOWN, tokens.get(i).getContent()));
				}
				else {
					throw new ParseException("Unknown token: " + token, 0);
				}
			}
		}
		return result;
	}

	public boolean isAllowUnscopedSeparators() {
		return allowUnscopedSeparators;
	}

	protected void setAllowUnscopedSeparators(boolean allowUnscopedSeparators) {
		this.allowUnscopedSeparators = allowUnscopedSeparators;
	}
}