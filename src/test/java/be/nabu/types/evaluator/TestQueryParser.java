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

package be.nabu.types.evaluator;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Analyzer;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.impl.PlainOperationProvider;

public class TestQueryParser extends TestCase {
	
	@SuppressWarnings("unchecked")
	public void testQuerier() throws ParseException, EvaluationException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		
		Operation<Object> operation = analyzer.analyze(parser.parse("5+5"));
		assertEquals(10, operation.evaluate(null));
		
		operation = analyzer.analyze(parser.parse("5+(5*2)"));
		assertEquals(15, operation.evaluate(null));

		operation = analyzer.analyze(parser.parse("5+(5*(2-(0.5*4)))"));
		assertEquals("5 + (5 * (2 - (0.5 * 4)))", operation.toString());
		assertEquals(5, operation.evaluate(null));
		
		// list support
		Test test = new Test("my", "myOther");
		operation = analyzer.analyze(parser.parse("tests[someOtherValue='my2']"));
		assertEquals("tests[someOtherValue = \"my2\"]", operation.toString());
		List<Test2> results = (List<Test2>) operation.evaluate(test);
		assertEquals(1, results.size());
		assertTrue(results.get(0).someValue.equals("my1"));
		
		// array support
		operation = analyzer.analyze(parser.parse("testsAsArray[someOtherValue='my2']"));
		results = (List<Test2>) operation.evaluate(test);
		assertEquals(1, results.size());
		assertTrue(results.get(0).someValue.equals("my1"));
		
		// matrix support
		operation = analyzer.analyze(parser.parse("testsAsArray[someOtherValue='my2'][0]/someValue"));
		assertEquals("testsAsArray[someOtherValue = \"my2\"][0]/someValue", operation.toString());
		String result = (String) operation.evaluate(test);
		assertEquals("my1", result);
	}
	
	public void testMod() throws ParseException, EvaluationException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("5 % 2"));
		assertEquals(1, operation.evaluate(null));
	}
	
	public void testOverload() throws ParseException, EvaluationException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("substring('test', 3) + substring('test', 1, 3)"));
		assertEquals("test", operation.evaluate(null));
	}
	
	public void testNull() throws ParseException, EvaluationException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("'test' == null"));
		assertEquals("\"test\" == null", operation.toString());
		assertFalse((Boolean) operation.evaluate(null));
	}
	
	public void testNegativeNumbers() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("0+1+-2+3*-1"));
		assertEquals("0 + 1 + -2 + (3 * -1)", operation.toString());
	}
	
	public void testStringification() throws ParseException, EvaluationException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("5-  (2 + 3)"));
		assertEquals(0, operation.evaluate(null));
		assertEquals("5 - (2 + 3)", operation.toString());
	}
	
	public void testWrongQuery() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		try {
			analyzer.analyze(parser.parse("1++2"));
			fail("This should fail during analysis");
		}
		catch (ParseException e) {
			// expected
		}
	}
	
	/**
	 * Note: currently it is unsure whether this is a bug or a feature
	 */
	public void testAlternativeVariableSyntax() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		// in the path analyzer two subsequent variables are indistinguishable from a single variable separted with slashes
		assertEquals("a/b", analyzer.analyze(parser.parse("a b")).toString());
	}
	
	public void testDanglingQuery() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		try {
			analyzer.analyze(parser.parse("else echo('test')"));
			fail("This should fail during analysis");
		}
		catch (ParseException e) {
			// expected
		}
	}
	
	public void testPrecedenceFormatting() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("1*2+3*4"));
		assertEquals("(1 * 2) + (3 * 4)", operation.toString());
		operation = analyzer.analyze(parser.parse("1*2/3*4"));
		assertEquals("1 * 2 / 3 * 4", operation.toString());
		operation = analyzer.analyze(parser.parse("test1++ + test2--"));
		assertEquals("(test1 ++) + (test2 --)", operation.toString());
	}
	
	public void testMethodFormatting() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("now() - 1"));
		assertEquals("now() - 1", operation.toString());
	}
	
	public void testDollarOnlyMethodNames() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("$(test)"));
		assertEquals("$(test)", operation.toString());
	}
	
	public void testDollarMethodNames() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		Operation<Object> operation = analyzer.analyze(parser.parse("$test()"));
		assertEquals("$test()", operation.toString());
	}
	
	public void testMethodAccess() throws ParseException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		
		// will try to access "myField" in the return value of the method
		Operation<Object> operation = analyzer.analyze(parser.parse("something()/myField"));
		assertEquals("something()/myField", operation.toString());
		
		// the same
		operation = analyzer.analyze(parser.parse("something() /myField"));
		assertEquals("something()/myField", operation.toString());
		
		// this will actually see the "/" as a divide operator 
		List<QueryPart> parse = parser.parse("something()/ myField");
		operation = analyzer.analyze(parse);
		assertEquals("something() / myField", operation.toString());
	}
	
	public void testVarargs() throws ParseException, EvaluationException {
		Analyzer<Object> analyzer = new PathAnalyzer<Object>(new PlainOperationProvider());
		QueryParser parser = QueryParser.getInstance();
		List<QueryPart> parse = parser.parse("choose(null, 'a')");
		Operation<Object> operation = analyzer.analyze(parse);
		assertEquals("a", operation.evaluate(null));
	}
	
	public static class Test {
		public String[] values;
		private List<Test2> tests = new ArrayList<Test2>();
		
		public Test(String...values) {
			this.values = values;
			for (String value : values)
				tests.add(new Test2(value + "1", value + "2"));
		}
		
		public Test2[] getTestsAsArray() {
			return tests.toArray(new Test2[0]);
		}

		public Object [][] getTestsAsMatrix() {
			Object[][] matrix = new Object[tests.size()][];
			for (int i = 0; i < tests.size(); i++) {
				matrix[i] = new Object[2];
				matrix[i][0] = tests.get(i).someValue;
				matrix[i][1] = tests.get(i).someOtherValue;
			}
			return matrix;
		}
	}
	
	public static class Test2 {
		public String someValue;
		private String someOtherValue;
		
		public Test2(String value1, String value2) {
			this.someValue = value1;
			this.someOtherValue = value2;
		}
	}
}
