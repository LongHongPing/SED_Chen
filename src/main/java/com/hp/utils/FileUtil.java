package com.hp.utils;

import java.io.File;

public class FileUtil {
    /** 获取路径下所有文件 */
    public static File[] getFiles(String path) {
        File file = new File(path);
        File[] files = file.listFiles();
        return files;
    }
    /** 删除文件 */
    public static void deleteFiles(String path){
        File[] files = getFiles(path);
        for (File file : files) {
            file.delete();
        }
    }
}
