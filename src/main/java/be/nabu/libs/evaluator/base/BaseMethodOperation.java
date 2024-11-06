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

import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;

abstract public class BaseMethodOperation<T> extends BaseOperation<T> {

	@Override
	public OperationType getType() {
		return OperationType.METHOD;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (!getParts().isEmpty()) {
			// first the method name
			builder.append(getParts().get(0).getContent().toString());
			// then the rest
			builder.append("(");
			for (int i = 1; i < getParts().size(); i++) {
				QueryPart part = getParts().get(i);
				if (i > 1) {
					builder.append(", ");
				}
				if (part.getType() == Type.STRING) {
					builder.append("\"" + formatString(part.getContent().toString()) + "\"");
				}
				else {
					builder.append(part.getContent() == null ? "null" : part.getContent().toString());
				}
			}
			builder.append(")");
		}
		else {
			builder.append("?(?)");
		}
		return builder.toString();
	}
}
