package de.ganskef.mocuishle;

public interface IFullTextIndex extends ITouched {

	void replaceIndex();

	long getIndexSize();

	// void insertOrReplace(BrowseDoc document);

}
