package com.sz.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.sz.aiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 资源下载工具
 * 用于从指定 URL 下载网络资源（如图片、文件、文档等），保存到本地指定目录。
 * 默认保存路径为：{@code FileConstant.FILE_SAVE_DIR + "/download"}
 * 适用于 Spring AI 工具系统，支持 AI 指定 URL 和文件名自动下载。
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class ResourceDownloadTool {

    /**
     * 下载网络资源到本地
     * @param url      要下载的资源链接
     * @param fileName 下载后保存的文件名（如 .png）
     * @return 下载成功或失败的提示信息
     * @author zyh
     * @date 2025/07/13
     */
    @Tool(description = "根据指定 URL 下载网络资源并保存到本地")
    public String downloadResource(
            @ToolParam(description = "要下载的资源 URL") String url,
            @ToolParam(description = "保存资源的文件名") String fileName) {
        // 拼接下载目录和完整文件路径
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建下载目录（若不存在）
            FileUtil.mkdir(fileDir);
            // 使用 Hutool 工具类发起下载并保存到本地文件
            HttpUtil.downloadFile(url, new File(filePath));
            // 返回成功提示
            log.info("资源已成功下载，保存路径为：" + filePath);
            return "资源已成功下载，保存路径为：" + filePath;
        } catch (Exception e) {
            // 异常处理
            log.error("下载资源异常：" + e.getMessage());
            return "下载资源失败，原因：" + e.getMessage();
        }
    }
}
