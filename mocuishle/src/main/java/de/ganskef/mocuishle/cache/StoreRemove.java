package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.DirectoryWalker;

public class StoreRemove extends DirectoryWalker<File> {

	private final McCache mCache;

	private File mBackupDir;

	public StoreRemove(McCache cache, Collection<File> markers) {
		mCache = cache;
	}

	public List<File> clean(File startDirectory) {
		try {
			walk(startDirectory, null);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return null;
	}

	@Override
	protected void handleDirectoryStart(File directory, int depth, Collection<File> results) throws IOException {
		mBackupDir = null;
		File dir = directory;
		String parentName = dir.getParentFile().getName();
		if (depth == 1 && (parentName.equals("https") || parentName.equals("http"))) {
			String name = "";
			for (int i = 0; i < depth; i++) {
				name = String.format("%s%s/", name, directory.getName(), "/");
				dir = dir.getParentFile();
			}
			name = String.format("%s%s%s", dir.getName(), "_/", name);
			mBackupDir = new File(dir.getParentFile(), name);
		}
	}

	@Override
	protected void handleDirectoryEnd(File directory, int depth, Collection<File> results) throws IOException {
		File[] files = directory.listFiles();
		if (depth > 0 && files.length <= 1) {
			if (files.length == 1 && files[0].getName().equals(".nomedia")) {
				files[0].delete();
			}
			directory.delete();
		}
	}

	protected void handleFile(File file, int depth, Collection<File> results) {
		if (mBackupDir != null && !mCache.isValidated(file)) {
			File dest = new File(mBackupDir, file.getName());
			mBackupDir.mkdirs();
			dest.delete();
			file.renameTo(dest);
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
		McCache.create(mocuishleDir).purgeInvalidateds();
	}
}
