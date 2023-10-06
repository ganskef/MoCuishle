package de.ganskef.mocuishle.modify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsertAfter implements IModifier {

	private Pattern mPattern;

	private String mReplacement;

	public InsertAfter(Pattern pattern, String replacement) {
		mPattern = pattern;
		mReplacement = replacement;
	}

	public StringBuilder modify(StringBuilder b) {
		Matcher m = mPattern.matcher(b);
		if (m.find()) {
			b.insert(m.end(), mReplacement);
		}
		return b;
	}
}
