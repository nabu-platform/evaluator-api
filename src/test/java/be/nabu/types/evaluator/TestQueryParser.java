package be.nabu.types.evaluator;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryParser;
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
		assertEquals(5, operation.evaluate(null));
		
		Test test = new Test("my", "myOther");
		operation = analyzer.analyze(parser.parse("tests[someOtherValue='my2']"));
		List<Test2> results = (List<Test2>) operation.evaluate(test);
		assertEquals(1, results.size());
		results.get(0).someValue.equals("my1");
	}
	
	public static class Test {
		public String[] values;
		private List<Test2> tests = new ArrayList<Test2>();
		
		public Test(String...values) {
			this.values = values;
			for (String value : values)
				tests.add(new Test2(value + "1", value + "2"));
		}
	}
	
	public static class Test2 {
		public String someValue;
		@SuppressWarnings("unused")
		private String someOtherValue;
		
		public Test2(String value1, String value2) {
			this.someValue = value1;
			this.someOtherValue = value2;
		}
	}
}
