package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.DirectoryWalker;

public class StoreRestore extends DirectoryWalker<File> {

	private final McCache mCache;

	private File mBackupDir;
	private File mRestoreDir;

	public StoreRestore(McCache cache) {
		mCache = cache;
	}

	public List<File> restore(String folderName) {
		mRestoreDir = mCache.getSpoolDir(folderName);
		mBackupDir = mCache.getSpoolDir(folderName + "_");
		try {
			walk(mBackupDir, null);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return null;
	}

	@Override
	protected void handleFile(File file, int depth, Collection<File> results) {
		if (file.getName().equals(".nomedia")) {
			file.delete();
			return;
		}
		File destDir = new File(mRestoreDir, file.getParentFile().getName());
		File destFile = new File(destDir, file.getName());
		if (!destFile.exists()) {
			destDir.mkdirs();
			file.renameTo(destFile);
		}
	}

	@Override
	protected void handleDirectoryEnd(File directory, int depth, Collection<File> results) throws IOException {
		System.out.println(directory);
		File[] files = directory.listFiles();
		if (depth > 0 && files.length == 0) {
			directory.delete();
		}
	}

	public static void main(String[] args) {
		if (args == null || args.length < 1) {
			System.out.println("Parameters needed: mocuishleDir");
			return;
		}
		File mocuishleDir = new File(args[0]);
		if (!new File(mocuishleDir, "https").isDirectory() || !new File(mocuishleDir, "http").isDirectory()) {
			System.out.println("Missed cache directories: http and https");
			return;
		}
		McCache mc = McCache.create(mocuishleDir);
		StoreRestore me = new StoreRestore(mc);
		me.restore("http");
		me.restore("https");
	}
}
