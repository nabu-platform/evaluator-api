package be.nabu.libs.evaluator.date;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import be.nabu.libs.evaluator.QueryPart.Type;

public class DateMethods {
	
	private static Map<String, String> dateFormats; static {
		dateFormats = new HashMap<String, String>();
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{1,3}$", "yyyy-MM-dd HH:mm:ss.S");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}$", "yyyy-MM-dd HH:mm:ss");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}$", "yyyy-MM-dd HH:mm");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}$", "yyyy-MM-dd HH");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2}$", "yyyy-MM-dd");
		dateFormats.put("^[0-9]{4}-[0-9]{2}$", "yyyy-MM");
		dateFormats.put("^[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{1,3}$", "HH:mm:ss.S");
		dateFormats.put("^[0-9]{2}:[0-9]{2}:[0-9]{2}$", "HH:mm:ss");
		dateFormats.put("^[0-9]{2}:[0-9]{2}$", "HH:mm");
		dateFormats.put("^[0-9]{2}$", "HH");
		
		dateFormats.put("^[0-9]{4}/[0-9]{2}/[0-9]{2}$", "yyyy/MM/dd");
		dateFormats.put("^[0-9]{4}/[0-9]{2}$", "yyyy/MM");
		dateFormats.put("^[0-9]{2}/[0-9]{2}/[0-9]{4}$", "dd/MM/yyyy");
		dateFormats.put("^[0-9]{2}/[0-9]{4}$", "MM/yyyy");
	}
	
	public static CustomDate now() {
		return new CustomDate();
	}

	/**
	 * This method tries to deduce the format from the value
	 */
	public static CustomDate date(String value) throws ParseException {
		if (value != null) {
			for (String regex : dateFormats.keySet()) {
				if (value.matches(regex)) {
					return parse(value, dateFormats.get(regex));
				}
			}
		}
		return null;
	}
	
	public static String format(CustomDate date, String format) {
		return format(date, format, TimeZone.getDefault());
	}
	
	public static String format(CustomDate date, String format, TimeZone timezone) {
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		formatter.setTimeZone(timezone);
		return formatter.format(date.getDate());
	}
	
	public static CustomDate parse(String date, String format) throws ParseException {
		return parse(date, format, TimeZone.getDefault());
	}
	public static CustomDate parse(String date, String format, TimeZone timezone) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		formatter.setTimeZone(timezone);
		return new CustomDate(formatter.parse(date));
	}
	
	public static CustomDate [] range(CustomDate from, CustomDate to, String increment) {
		List<CustomDate> dates = new ArrayList<CustomDate>();
		while (!from.getDate().after(to.getDate())) {
			dates.add(from);
			from = CustomDate.increment(from, increment, Type.ADD);
		}
		return dates.toArray(new CustomDate[dates.size()]);
	}
	
	public static String [] range(CustomDate from, CustomDate to, String increment, String format) {
		return range(from, to, increment, format, TimeZone.getDefault());
	}
	
	public static String [] range(CustomDate from, CustomDate to, String increment, String format, TimeZone timezone) {
		List<String> result = new ArrayList<String>();
		for (CustomDate generated : range(from.normalize(format), to.normalize(format), increment)) {
			result.add(format(generated, format, timezone));
		}
		return result.toArray(new String[result.size()]);
	}
}
