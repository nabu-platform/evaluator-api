package be.nabu.libs.evaluator.api;

import java.text.ParseException;
import java.util.List;

import be.nabu.libs.evaluator.QueryPart;

public interface Analyzer<T> {
	public Operation<T> analyze(List<QueryPart> tokens) throws ParseException;
}
