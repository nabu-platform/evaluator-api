package be.nabu.libs.evaluator.api;

import java.text.ParseException;
import java.util.List;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;

public interface Operation<T> {	
	public void add(QueryPart part);
	public void finish() throws ParseException;
	public List<QueryPart> getParts();
	public Object evaluate(T context) throws EvaluationException;
	public OperationType getType();
}
