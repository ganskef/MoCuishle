package de.ganskef.mocuishle.util;

public class LongUtil {

	// Introduced for Java 6 compatibility, use Long.compare in Java 7
	public static final int compareLong(long thisVal, long anotherVal) {
		return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
	}

	private LongUtil() {
	}
}
