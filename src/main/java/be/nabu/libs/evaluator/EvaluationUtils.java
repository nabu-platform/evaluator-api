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

package be.nabu.libs.evaluator;

import java.text.ParseException;

import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;

public class EvaluationUtils {
	
	@SuppressWarnings("unchecked")
	public static <T> Operation<T> clone(Operation<T> operation, OperationProvider<T> provider) throws ParseException {
		Operation<T> clone = provider.newOperation(operation.getType());
		for (QueryPart part : operation.getParts()) {
			// if the part is a nested operation, clone it
			if (part.getContent() instanceof Operation) {
				clone.add(new QueryPart(part.getToken(), part.getType(), clone((Operation<T>) part.getContent(), provider)));
			}
			else {
				clone.add(part.clone());
			}
		}
		clone.finish();
		return clone;
	}
}
