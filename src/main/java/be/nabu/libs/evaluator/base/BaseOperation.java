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

package be.nabu.libs.evaluator.base;

import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
/**
 * An operation (contrary to what you might think) does NOT require an operator
 * It does however need to return a value
 * Apart from operators, a value can be gotten from variables (indexed or regular) or methods
 * 
 * It's important to note that the bitwise or "|" operator is NOT executed like in xpath, but like in java
 * The result of an operation must always be of a SINGLE type, the xpath concat could break this and as such is not allowed
 * 
 * Note that if an operator is present, it will have just enough other operands to make it work, meaning "6+5+3" will actually result in two operations: 6+subOperation where subOperation = 5+3
 * Therefore there is no need to keep "intermediate" results for operator calculations
 * 
 * @author alex
 *
 */
abstract public class BaseOperation<T> implements Operation<T> {
	
	private List<QueryPart> parts = new ArrayList<QueryPart>();
	
	@Override
	public void add(QueryPart part) {
		parts.add(part);
	}
	
	@Override
	public List<QueryPart> getParts() {
		return parts;
	}
	
	@Override
	public String toString() {
		return toTreeString();
	}
	
	public String toTreeString() {
		return toTreeString(0);
	}
	
	/**
	 * Needs to be optimized
	 */
	@SuppressWarnings("unchecked")
	private String toTreeString(int depth) {
		String content = "";
		for (QueryPart part : parts) {
			if (part.getType() == QueryPart.Type.OPERATION)
				content += getTabs(depth) + "(\n" + ((BaseOperation<T>) part.getContent()).toTreeString(depth + 1) + "\n" + getTabs(depth) + ")";
			else
				content += (content.length() > 0 ? ",\n"  : "") + getTabs(depth) + part.getType() + "[" + part.getContent() + "]";
		}
		return content;	
	}
	
	private String getTabs(int amount) {
		String tabs = "";
		for (int i = 0; i < amount; i++)
			tabs += "\t";
		return tabs;
	}
	
	protected String formatString(String string) {
		return string.replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r");
	}
}
