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
