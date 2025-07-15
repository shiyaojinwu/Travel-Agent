package com.sz.aiagent.constant;

/**
 * 文件常量
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
public interface FileConstant {

    /**
     * 文件保存目录(当前 Java 程序运行的工作目录+ "/tmp")
     */
    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
}
