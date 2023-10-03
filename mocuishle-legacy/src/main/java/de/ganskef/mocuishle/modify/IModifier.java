package de.ganskef.mocuishle.modify;

public interface IModifier {

	// FIXME refactor StringBuilder to CharSequence
	StringBuilder modify(StringBuilder b);
}
