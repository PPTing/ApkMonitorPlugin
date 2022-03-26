package me.ppting.plugin.utils;

import java.io.File;

/**
 * Created by PPTing on 2022/3/26.
 * Description:
 */

public class FileUtils {
    public static boolean deleteFile(File dirFile) {
        // 如果dir对应的文件不存在，则退出
        if (!dirFile.exists()) {
            return false;
        }

        if (dirFile.isFile()) {
            return dirFile.delete();
        } else {

            for (File file : dirFile.listFiles()) {
                deleteFile(file);
            }
        }

        return dirFile.delete();
    }
}
