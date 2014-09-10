package be.nabu.libs.evaluator;

import java.util.List;

public class Methods {

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
	
	public static Integer count(Object object) {
		if (object instanceof List)
			return ((List<?>) object).size();
		else if (object instanceof Object[])
			return ((Object[]) object).length;
		else
			return 1;
	}
}
