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

package be.nabu.libs.evaluator.api;

import be.nabu.libs.evaluator.EvaluationException;

public interface ContextAccessor<T> {
	public Class<T> getContextType();
	// check whether the context has a particular field
	public boolean has(T context, String name) throws EvaluationException;
	// Check if an object has a value for a given path
	// By default we assume if the type supports it, that the content has _a_ value (could be null)
	// If you have smarter implementations that can track changes, you can be more specific in your answer and state whether someone explicitly set a value
	public default boolean hasValue(T context, String name) throws EvaluationException {
		return has(context, name);
	}
	public Object get(T context, String name) throws EvaluationException;
}
