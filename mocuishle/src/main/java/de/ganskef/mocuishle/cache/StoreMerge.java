package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import org.apache.commons.io.DirectoryWalker;

/**
 * Merge two backups into one directory containing all data. Duplicated files
 * with other content stay in the older directory.
 */
public class StoreMerge extends DirectoryWalker<File> {

	private File mTargetDir;

	public StoreMerge(File targetDir) {
		Objects.requireNonNull(targetDir);
		if (!targetDir.isDirectory() && !targetDir.mkdirs()) {
			throw new IllegalArgumentException("Needed directory targetDir=" + targetDir);
		}
		mTargetDir = targetDir;
	}

	@Override
	protected void handleDirectoryStart(File directory, int depth, java.util.Collection<File> results)
			throws IOException {
		System.out.println(directory);
		if (depth == 1) {
			String name = String.format("%s/%s", directory.getParentFile().getName(), directory.getName());
			File dest = new File(mTargetDir, name);
			if (!dest.exists() && !dest.mkdirs()) {
				throw new IllegalStateException("Dest dir couldn't be created " + dest);
			}
		}
	}

	@Override
	protected void handleDirectoryEnd(File directory, int depth, Collection<File> results) throws IOException {
		File[] files = directory.listFiles();
		if (depth > 0 && files.length <= 1) {
			if (files.length == 1 && files[0].getName().equals(".nomedia")) {
				if (!files[0].delete()) {
					throw new IllegalStateException("File couldn't be deleted " + files[0]);
				}
			}
			directory.delete();
		}
	}

	protected void handleFile(File file, int depth, java.util.Collection<File> results) throws IOException {
		if (depth == 1) {
			handleFile(file);
		}
	}

	private void handleFile(File file) {
		File dest = destination(file);
		if (dest.exists()) {
			// The same URL (hash) with the same length should be the same content.
			if (file.length() == dest.length()) {
				if (!file.delete()) {
					throw new IllegalStateException("File couldn't be deleted " + file);
				}
			} else {
				System.out.println("Different " + file);
			}
		} else {
			if (!file.renameTo(dest)) {
				throw new IllegalStateException("File couldn't be moved " + file + " to " + dest);
			}
		}
	}

	private File destination(File file) {
		File directory = file.getParentFile();
		String name = String.format("%s/%s/%s", directory.getParentFile().getName(), directory.getName(),
				file.getName());
		return new File(mTargetDir, name);
	}

	public void reorg(File importDir) {
		Objects.requireNonNull(importDir);
		if (!importDir.isDirectory()) {
			throw new IllegalArgumentException("Needed directory importDir=" + importDir);
		}
		try {
			walk(new File(importDir, "https"), null);
			walk(new File(importDir, "http"), null);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static void main(String[] args) {
		if (args == null || args.length < 2) {
			System.out.println("Parameters needed: targetDir, importDir(s)");
			return;
		}
		File targetDir = new File(args[0]);
		StoreMerge merge = new StoreMerge(targetDir);
		for (int i = 1; i < args.length; i++) {
			File importDir = new File(args[i]);
			merge.reorg(importDir);
		}
	}
}
