package com.antfortune.freeline

import groovy.json.JsonSlurper
import org.gradle.api.Project

import java.nio.channels.FileChannel

/**
 * Created by huangyong on 16/7/19.
 */
class FreelineUtils {

    public static String getRelativePath(File root, File target) {
        String path = target.absolutePath.replace(root.absolutePath, "")
        while (path.startsWith("/") || (path.startsWith("\\"))) {
            path = path.substring(1)
        }
        return path
    }

    public static def getProperty(Project project, String property) {
        if (project.hasProperty(property)) {
            return project.getProperties()[property];
        }
        return null;
    }

    public static String getFreelineCacheDir(String rootDirPath) {
        String projectCacheDir = FreelineGenerator.generateStringMD5(rootDirPath)
        def freelineCacheDir = new File(FreelineUtils.joinPath(System.properties['user.home'].toString(), ".freeline", "cache", projectCacheDir))
        if (!freelineCacheDir.exists() || !freelineCacheDir.isDirectory()) {
            freelineCacheDir.mkdirs()
        }
        return freelineCacheDir.absolutePath
    }

    public static String getDefaultApkPath(List<String> apks, String buildDir, String projectName, String productFlavor) {
        if (apks == null || apks.size() == 0) {
            return FreelineGenerator.generateApkPath(buildDir, projectName, productFlavor)
        }
        if (apks.size() == 1) {
            return apks.get(0)
        }
        for (String path : apks) {
            if (path.contains("-armeabi-")) {
                return path
            }
        }
        return apks.get(0)
    }

    public static String getBuildCacheDir(String buildDirPath) {
        def buildCacheDir = new File(buildDirPath, Constants.FREELINE_BUILD_CACHE_DIR)
        if (!buildCacheDir.exists() || !buildCacheDir.isDirectory()) {
            buildCacheDir.mkdirs()
        }
        return buildCacheDir.absolutePath
    }

    public static String getBuildAssetsDir(String buildDirPath) {
        def buildAssetsDir = new File(getBuildCacheDir(buildDirPath), "freeline-assets")
        if (!buildAssetsDir.exists() || !buildAssetsDir.isDirectory()) {
            buildAssetsDir.mkdirs()
        }
        return buildAssetsDir.absolutePath
    }

    public static String getBuildBackupDir(String buildDirPath) {
        def buildBackupDir = new File(getBuildCacheDir(buildDirPath), "freeline-backup")
        if (!buildBackupDir.exists() || !buildBackupDir.isDirectory()) {
            buildBackupDir.mkdirs()
        }
        return buildBackupDir.absolutePath
    }

    public static def getJson(String url) {
        return new JsonSlurper().parseText(new URL(url).text)
    }

    public static boolean saveJson(String json, String fileName, boolean override) {
        def pending = new File(fileName)
        if (pending.exists() && pending.isFile()) {
            if (override) {
                println "Old file $pending.absolutePath removed."
                pending.delete()
            } else {
                println "File $pending.absolutePath exists."
                return false
            }
        }

        pending << json
        println "Save to $pending.absolutePath"
        return true
    }

    public static String joinPath(String... sep) {
        if (sep.length == 0) {
            return "";
        }
        if (sep.length == 1) {
            return sep[0];
        }

        return new File(sep[0], joinPath(Arrays.copyOfRange(sep, 1, sep.length))).getPath();
    }

    public static String getOsName() {
        return System.getProperty("os.name");
    }

    public static boolean isWindows() {
        return getOsName().startsWith("Windows");
    }

    public static boolean isEmpty(String text) {
        return text == null || text == '' || text.trim() == ''
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    // ------------ from commons-io

    /**
     * Deletes a file, never throwing an exception. If file is a directory, delete it and all sub-directories.
     * <p>
     * The difference between File.delete() and this method are:
     * <ul>
     * <li>A directory to be deleted does not have to be empty.</li>
     * <li>No exceptions are thrown when a file or directory cannot be deleted.</li>
     * </ul>
     *
     * @param file  file or directory to delete, can be {@code null}
     * @return {@code true} if the file or directory was deleted, otherwise
     * {@code false}
     *
     * @since 1.4
     */
    public static boolean deleteQuietly(File file) {
        if (file == null) {
            return false;
        }
        try {
            if (file.isDirectory()) {
                cleanDirectory(file);
            }
        } catch (Exception ignored) {
        }

        try {
            return file.delete();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Cleans a directory without deleting it.
     *
     * @param directory directory to clean
     * @throws IOException in case cleaning is unsuccessful
     */
    public static void cleanDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    /**
     * Deletes a file. If file is a directory, delete it and all sub-directories.
     * <p>
     * The difference between File.delete() and this method are:
     * <ul>
     * <li>A directory to be deleted does not have to be empty.</li>
     * <li>You get exceptions when a file or directory cannot be deleted.
     *      (java.io.File methods returns a boolean)</li>
     * </ul>
     *
     * @param file  file or directory to delete, must not be {@code null}
     * @throws NullPointerException if the directory is {@code null}
     * @throws FileNotFoundException if the file was not found
     * @throws IOException in case deletion is unsuccessful
     */
    public static void forceDelete(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent){
                    throw new FileNotFoundException("File does not exist: " + file);
                }
                String message =
                        "Unable to delete file: " + file;
                throw new IOException(message);
            }
        }
    }

    /**
     * Deletes a directory recursively.
     *
     * @param directory  directory to delete
     * @throws IOException in case deletion is unsuccessful
     */
    public static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        if (!isSymlink(directory)) {
            cleanDirectory(directory);
        }

        if (!directory.delete()) {
            String message =
                    "Unable to delete directory " + directory + ".";
            throw new IOException(message);
        }
    }

    /**
     * Determines whether the specified file is a Symbolic Link rather than an actual file.
     * <p>
     * Will not return true if there is a Symbolic Link anywhere in the path,
     * only if the specific file is.
     * <p>
     * <b>Note:</b> the current implementation always returns {@code false} if the system
     * is detected as Windows using {@link FilenameUtils#isSystemWindows()}
     *
     * @param file the file to check
     * @return true if the file is a Symbolic Link
     * @throws IOException if an IO error occurs while checking the file
     * @since 2.0
     */
    public static boolean isSymlink(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("File must not be null");
        }
        if (isWindows()) {
            return false;
        }
        File fileInCanonicalDir = null;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }

        if (fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Copies a whole directory to a new location preserving the file dates.
     * <p>
     * This method copies the specified directory and all its child
     * directories and files to the specified destination.
     * The destination is the new location and name of the directory.
     * <p>
     * The destination directory is created if it does not exist.
     * If the destination directory did exist, then this method merges
     * the source with the destination, with the source taking precedence.
     * <p>
     * <strong>Note:</strong> This method tries to preserve the files' last
     * modified date/times using {@link File#setLastModified(long)}, however
     * it is not guaranteed that those operations will succeed.
     * If the modification operation fails, no indication is provided.
     *
     * @param srcDir  an existing directory to copy, must not be {@code null}
     * @param destDir  the new directory, must not be {@code null}
     *
     * @throws NullPointerException if source or destination is {@code null}
     * @throws IOException if source or destination is invalid
     * @throws IOException if an IO error occurs during copying
     * @since 1.1
     */
    public static void copyDirectory(File srcDir, File destDir) throws IOException {
        copyDirectory(srcDir, destDir, true);
    }

    /**
     * Copies a whole directory to a new location.
     * <p>
     * This method copies the contents of the specified source directory
     * to within the specified destination directory.
     * <p>
     * The destination directory is created if it does not exist.
     * If the destination directory did exist, then this method merges
     * the source with the destination, with the source taking precedence.
     * <p>
     * <strong>Note:</strong> Setting <code>preserveFileDate</code> to
     * {@code true} tries to preserve the files' last modified
     * date/times using {@link File#setLastModified(long)}, however it is
     * not guaranteed that those operations will succeed.
     * If the modification operation fails, no indication is provided.
     *
     * @param srcDir  an existing directory to copy, must not be {@code null}
     * @param destDir  the new directory, must not be {@code null}
     * @param preserveFileDate  true if the file date of the copy
     *  should be the same as the original
     *
     * @throws NullPointerException if source or destination is {@code null}
     * @throws IOException if source or destination is invalid
     * @throws IOException if an IO error occurs during copying
     * @since 1.1
     */
    public static void copyDirectory(File srcDir, File destDir,
                                     boolean preserveFileDate) throws IOException {
        copyDirectory(srcDir, destDir, null, preserveFileDate);
    }

    /**
     * Copies a filtered directory to a new location preserving the file dates.
     * <p>
     * This method copies the contents of the specified source directory
     * to within the specified destination directory.
     * <p>
     * The destination directory is created if it does not exist.
     * If the destination directory did exist, then this method merges
     * the source with the destination, with the source taking precedence.
     * <p>
     * <strong>Note:</strong> This method tries to preserve the files' last
     * modified date/times using {@link File#setLastModified(long)}, however
     * it is not guaranteed that those operations will succeed.
     * If the modification operation fails, no indication is provided.
     *
     * <h4>Example: Copy directories only</h4>
     *  <pre>
     *  // only copy the directory structure
     *  FileUtils.copyDirectory(srcDir, destDir, DirectoryFileFilter.DIRECTORY);
     *  </pre>
     *
     * <h4>Example: Copy directories and txt files</h4>
     *  <pre>
     *  // Create a filter for ".txt" files
     *  IOFileFilter txtSuffixFilter = FileFilterUtils.suffixFileFilter(".txt");
     *  IOFileFilter txtFiles = FileFilterUtils.andFileFilter(FileFileFilter.FILE, txtSuffixFilter);
     *
     *  // Create a filter for either directories or ".txt" files
     *  FileFilter filter = FileFilterUtils.orFileFilter(DirectoryFileFilter.DIRECTORY, txtFiles);
     *
     *  // Copy using the filter
     *  FileUtils.copyDirectory(srcDir, destDir, filter);
     *  </pre>
     *
     * @param srcDir  an existing directory to copy, must not be {@code null}
     * @param destDir  the new directory, must not be {@code null}
     * @param filter  the filter to apply, null means copy all directories and files
     *  should be the same as the original
     *
     * @throws NullPointerException if source or destination is {@code null}
     * @throws IOException if source or destination is invalid
     * @throws IOException if an IO error occurs during copying
     * @since 1.4
     */
    public static void copyDirectory(File srcDir, File destDir,
                                     FileFilter filter) throws IOException {
        copyDirectory(srcDir, destDir, filter, true);
    }

    /**
     * Copies a filtered directory to a new location.
     * <p>
     * This method copies the contents of the specified source directory
     * to within the specified destination directory.
     * <p>
     * The destination directory is created if it does not exist.
     * If the destination directory did exist, then this method merges
     * the source with the destination, with the source taking precedence.
     * <p>
     * <strong>Note:</strong> Setting <code>preserveFileDate</code> to
     * {@code true} tries to preserve the files' last modified
     * date/times using {@link File#setLastModified(long)}, however it is
     * not guaranteed that those operations will succeed.
     * If the modification operation fails, no indication is provided.
     *
     * <h4>Example: Copy directories only</h4>
     *  <pre>
     *  // only copy the directory structure
     *  FileUtils.copyDirectory(srcDir, destDir, DirectoryFileFilter.DIRECTORY, false);
     *  </pre>
     *
     * <h4>Example: Copy directories and txt files</h4>
     *  <pre>
     *  // Create a filter for ".txt" files
     *  IOFileFilter txtSuffixFilter = FileFilterUtils.suffixFileFilter(".txt");
     *  IOFileFilter txtFiles = FileFilterUtils.andFileFilter(FileFileFilter.FILE, txtSuffixFilter);
     *
     *  // Create a filter for either directories or ".txt" files
     *  FileFilter filter = FileFilterUtils.orFileFilter(DirectoryFileFilter.DIRECTORY, txtFiles);
     *
     *  // Copy using the filter
     *  FileUtils.copyDirectory(srcDir, destDir, filter, false);
     *  </pre>
     *
     * @param srcDir  an existing directory to copy, must not be {@code null}
     * @param destDir  the new directory, must not be {@code null}
     * @param filter  the filter to apply, null means copy all directories and files
     * @param preserveFileDate  true if the file date of the copy
     *  should be the same as the original
     *
     * @throws NullPointerException if source or destination is {@code null}
     * @throws IOException if source or destination is invalid
     * @throws IOException if an IO error occurs during copying
     * @since 1.4
     */
    public static void copyDirectory(File srcDir, File destDir,
                                     FileFilter filter, boolean preserveFileDate) throws IOException {
        if (srcDir == null) {
            throw new NullPointerException("Source must not be null");
        }
        if (destDir == null) {
            throw new NullPointerException("Destination must not be null");
        }
        if (srcDir.exists() == false) {
            throw new FileNotFoundException("Source '" + srcDir + "' does not exist");
        }
        if (srcDir.isDirectory() == false) {
            throw new IOException("Source '" + srcDir + "' exists but is not a directory");
        }
        if (srcDir.getCanonicalPath().equals(destDir.getCanonicalPath())) {
            throw new IOException("Source '" + srcDir + "' and destination '" + destDir + "' are the same");
        }

        // Cater for destination being directory within the source directory (see IO-141)
        List<String> exclusionList = null;
        if (destDir.getCanonicalPath().startsWith(srcDir.getCanonicalPath())) {
            File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
            if (srcFiles != null && srcFiles.length > 0) {
                exclusionList = new ArrayList<String>(srcFiles.length);
                for (File srcFile : srcFiles) {
                    File copiedFile = new File(destDir, srcFile.getName());
                    exclusionList.add(copiedFile.getCanonicalPath());
                }
            }
        }
        doCopyDirectory(srcDir, destDir, filter, preserveFileDate, exclusionList);
    }

    /**
     * Internal copy directory method.
     *
     * @param srcDir  the validated source directory, must not be {@code null}
     * @param destDir  the validated destination directory, must not be {@code null}
     * @param filter  the filter to apply, null means copy all directories and files
     * @param preserveFileDate  whether to preserve the file date
     * @param exclusionList  List of files and directories to exclude from the copy, may be null
     * @throws IOException if an error occurs
     * @since 1.1
     */
    private static void doCopyDirectory(File srcDir, File destDir, FileFilter filter,
                                        boolean preserveFileDate, List<String> exclusionList) throws IOException {
        // recurse
        File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
        if (srcFiles == null) {  // null if abstract pathname does not denote a directory, or if an I/O error occurs
            throw new IOException("Failed to list contents of " + srcDir);
        }
        if (destDir.exists()) {
            if (destDir.isDirectory() == false) {
                throw new IOException("Destination '" + destDir + "' exists but is not a directory");
            }
        } else {
            if (!destDir.mkdirs() && !destDir.isDirectory()) {
                throw new IOException("Destination '" + destDir + "' directory cannot be created");
            }
        }
        if (destDir.canWrite() == false) {
            throw new IOException("Destination '" + destDir + "' cannot be written to");
        }
        for (File srcFile : srcFiles) {
            File dstFile = new File(destDir, srcFile.getName());
            if (exclusionList == null || !exclusionList.contains(srcFile.getCanonicalPath())) {
                if (srcFile.isDirectory()) {
                    doCopyDirectory(srcFile, dstFile, filter, preserveFileDate, exclusionList);
                } else {
                    copyFile(srcFile, dstFile);
                }
            }
        }

        // Do this last, as the above has probably affected directory metadata
        if (preserveFileDate) {
            destDir.setLastModified(srcDir.lastModified());
        }
    }

}
