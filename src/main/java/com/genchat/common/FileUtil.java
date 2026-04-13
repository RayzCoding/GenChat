package com.genchat.common;

import java.util.Set;

/**
 * File utility class
 */
public final class FileUtil {

    private FileUtil() {
    }

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "pdf", "doc", "docx"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif"
    );

    private static final int LARGE_TEXT_THRESHOLD = 2000;

    /**
     * Extract file extension from filename (without dot, lowercase)
     */
    public static String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Check if the file is a plain text file
     */
    public static boolean isTextFile(String filename) {
        return TEXT_EXTENSIONS.contains(getFileType(filename));
    }

    /**
     * Check if the file is an image file
     */
    public static boolean isImageFile(String filename) {
        return IMAGE_EXTENSIONS.contains(getFileType(filename));
    }

    /**
     * Check if the text content exceeds the large text threshold (3000 characters)
     */
    public static boolean isLargeTextFile(String text) {
        return text != null && text.length() > LARGE_TEXT_THRESHOLD;
    }
}
