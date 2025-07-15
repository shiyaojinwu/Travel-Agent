package com.sz.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.sz.aiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 文件操作工具类
 * 提供基础的文件读写功能，支持读取指定文件内容和写入文本到指定文件。
 * 适用于 AI 调用，实现文件内容的动态获取和存储。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class FileOperationTool {

    /**
     * 文件保存的基础目录，位于全局配置的文件根目录下的 /file 子目录
     */
    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    /**
     * 📖 读取文件内容
     * 根据文件名读取对应文件的 UTF-8 编码内容。
     * @param fileName 要读取的文件名（如 example.txt）
     * @return 读取的文件内容，或错误信息
     * @author zyh
     * @date 2025/07/13
     */
    @Tool(description = "读取指定文件的文本内容")
    public String readFile(
            @ToolParam(description = "要读取的文件名") String fileName) {

        String filePath = FILE_DIR + "/" + fileName;
        try {
            // 读取整个文件的 UTF-8 内容为字符串返回
            log.info("开始读取文件：{}", filePath);
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            // 发生异常则返回错误提示
            log.error("读取文件失败：{}", filePath, e);
            return "读取文件失败，错误原因：" + e.getMessage();
        }
    }

    /**
     * 写入内容到文件
     * 将指定内容以 UTF-8 编码写入指定文件，目录不存在时会自动创建。
     * @param fileName 文件名（如 output.txt）
     * @param content  要写入文件的文本内容
     * @return 写入成功或失败提示
     * @author zyh
     * @date 2025/07/13
     */
    @Tool(description = "写入文本内容到指定文件")
    public String writeFile(
            @ToolParam(description = "要写入的文件名") String fileName,
            @ToolParam(description = "写入文件的文本内容") String content) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            // 确保目录存在，若不存在则自动创建
            FileUtil.mkdir(FILE_DIR);
            // 将文本内容写入文件（覆盖写入）
            FileUtil.writeUtf8String(content, filePath);
            log.info("文件写入成功，保存路径：" + filePath);
            return "文件写入成功，保存路径：" + filePath;
        } catch (Exception e) {
            // 发生异常返回错误信息
            log.error("写入文件失败：{}", e.getMessage(), e);
            return "写入文件失败，错误原因：" + e.getMessage();
        }
    }
}
