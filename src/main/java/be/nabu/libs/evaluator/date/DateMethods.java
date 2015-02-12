package be.nabu.libs.evaluator.date;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import be.nabu.libs.evaluator.QueryPart.Type;

public class DateMethods {
	
	public static CustomDate now() {
		return new CustomDate();
	}

	/**
	 * This method tries to deduce the format from the value
	 * TODO: it should be expanded to inspect the value and (taking the region into account for default formats), attempt to guess the format
	 */
	public static CustomDate date(String value) throws ParseException {
		return parse(value, "yyyy/MM/dd");
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
