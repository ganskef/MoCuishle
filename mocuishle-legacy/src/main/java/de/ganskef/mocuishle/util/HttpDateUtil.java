package de.ganskef.mocuishle.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ObjectUtils;

/**
 * Convert HTTP Dates to a comparable String like this: 19941106-08:49:37.
 *
 * <ul>
 * <li>Sun, 06 Nov 1994 08:49:37 GMT: standard specification
 * <li>Sun, 06-Nov-1994 08:49:37 GMT: obsolete specification
 * <li>Sun Nov 6 08:49:37 1994: obsolete specification
 * </ul>
 */
public enum HttpDateUtil {
	STANDARD("[\\w,]+ (\\d+) (\\w{3}) (\\d+) (\\d+:\\d+:\\d+) GMT", 3, 2, 1, 4),

	OBSOLETE1("[\\w,]+ (\\d+)-(\\w{3})-(\\d+) (\\d+:\\d+:\\d+) GMT", 3, 2, 1, 4),

	OBSOLETE2("\\w+ (\\w{3}) (\\d+) (\\d+:\\d+:\\d+) (\\d+)", 4, 1, 2, 3);

	private static final String FORMAT = "%04d%02d%02d-%s";

	private static final String[] MONTHS = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
			"Nov", "Dec" };

	private Pattern mPattern;

	private int mYearPosition, mMonthPosition, mDayPosition, mTimePosition;

	private HttpDateUtil(String regex, int yearPosition, int monthPosition, int dayPosition, int timePosition) {
		this.mPattern = Pattern.compile(regex);
		this.mYearPosition = yearPosition;
		this.mMonthPosition = monthPosition;
		this.mDayPosition = dayPosition;
		this.mTimePosition = timePosition;
	}

	private String convert(String input) {
		Matcher m = mPattern.matcher(input);
		if (m.matches()) {
			int year = Integer.parseInt(m.group(mYearPosition));
			int month = month(m.group(mMonthPosition));
			int day = Integer.parseInt(m.group(mDayPosition));
			String time = m.group(mTimePosition);
			return String.format((Locale) null, FORMAT, year, month, day, time);
		}
		return null;
	}

	private int month(String name) {
		for (int i = 0; i < 12; i++) {
			if (name.equals(MONTHS[i])) {
				return i + 1;
			}
		}
		throw new NumberFormatException("Illegal month name " + name);
	}

	public static final String comparable(String input) {
		if (input != null) {
			for (HttpDateUtil each : values()) {
				String result = each.convert(input);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	public static final boolean lt(String first, String second) {
		return ObjectUtils.compare(comparable(first), comparable(second)) < 0;
	}

	public static final boolean le(String first, String second) {
		return ObjectUtils.compare(comparable(first), comparable(second)) <= 0;
	}

	public static final boolean gt(String first, String second) {
		return ObjectUtils.compare(comparable(first), comparable(second)) > 0;
	}

	public static final boolean ge(String first, String second) {
		return ObjectUtils.compare(comparable(first), comparable(second)) >= 0;
	}
}
