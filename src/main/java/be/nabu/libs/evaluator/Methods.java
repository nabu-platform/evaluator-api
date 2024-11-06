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

import java.util.List;

public class Methods {

	public static Object choose(Object...possiblities) {
		for (int i = 0; i < possiblities.length; i++) {
			if (possiblities[i] != null) {
				return possiblities[i];
			}
		}
		return null;
	}
	
	public static boolean exists(Object object) {
		return object != null;
	}
	
	public static boolean not(Boolean result) {
		return result == null || !result;
	}
	
	public static String substringAfter(String original, String target) {
		return original.substring(
			original.indexOf(target) + target.length()
		);
	}
	
	public static String substring(String original, int offset) {
		return original.substring(offset);
	}
	
	public static String substring(String original, int offset, int length) {
		return original.substring(offset, offset + length);
	}
	
	public static Integer count(Object object) {
		if (object instanceof List)
			return ((List<?>) object).size();
		else if (object instanceof Object[])
			return ((Object[]) object).length;
		else
			return 1;
	}
}
