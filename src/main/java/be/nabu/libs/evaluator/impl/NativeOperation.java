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

package be.nabu.libs.evaluator.impl;

import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;


/**
 * Can return the type of the operation
 * 
 * @author alex
 *
 */
public class NativeOperation<T> extends BaseOperation<T> {
	
	@Override
	public Object evaluate(T context) throws EvaluationException {
		return getParts().get(0).getContent();
	}
	@Override
	public void finish() {
		// do nothing
	}
	@Override
	public OperationType getType() {
		return OperationType.NATIVE;
	}
	
	@Override
	public String toString() {
		if (getParts().get(0).getType() == Type.STRING) {
			String content = formatString(getParts().get(0).getContent().toString());
			// if the string contains a double quote and no single quotes, wrap it in a single instead
			if (content.indexOf('"') >= 0 && content.indexOf('\'') < 0) {
				return "'" + content + "'";
			}
			else {
				return "\"" + content + "\"";
			}
		}
		else {
			return getParts().get(0).getContent() == null ? "null" : getParts().get(0).getContent().toString();
		}
	}
}
