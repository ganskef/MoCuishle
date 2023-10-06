package de.ganskef.mocuishle.modify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsertBefore implements IModifier {

	private Pattern mPattern;

	private String mReplacement;

	public InsertBefore(Pattern pattern, String replacement) {
		mPattern = pattern;
		mReplacement = replacement;
	}

	public StringBuilder modify(StringBuilder b) {
		Matcher m = mPattern.matcher(b);
		if (m.find()) {
			b.insert(m.start(), mReplacement);
		}
		return b;
	}
}
