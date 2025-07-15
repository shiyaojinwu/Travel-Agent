package com.sz.aiagentmcp;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图片搜索工具类，通过 Pexels API 从网上搜索图片
 * 提供给 AI Agent 调用，支持关键词搜索中等尺寸图片
 * 注意：使用前需替换 API_KEY 为你自己的 Pexels API 密钥
 * 官网：https://www.pexels.com/api/
 * @author zyh
 * @version 1.0.0
 * @date 2025/7/15
 */
@Service
public class ImageSearchTool {

    /**
     *Pexels 平台申请的 API 密钥（必须替换为真实有效的密钥）
     */
    private static final String API_KEY = "WQa9SUifHW2nW9dC92pxfXUyNdR9wwEGb3xmCclVC8CE6gjQIcsgizu4";

    /**
     * Pexels 搜索接口 URL（支持 GET 请求）
     */
    private static final String API_URL = "https://api.pexels.com/v1/search";

    /**
     * AI 工具调用入口：根据关键词搜索图片
     * @param query 搜索关键词，如 "cat"、"sunset" 等
     * @return 返回中等尺寸图片的 URL 列表（逗号分隔），或错误信息
     * @author zyh
     * @date 2025/07/15
     */
    @Tool(description = "search image from web2")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            // 调用搜索中等尺寸图片的方法，并用逗号连接成字符串返回
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            // 捕获异常并返回错误信息
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 根据关键词搜索图片，并提取每张图片的 medium 尺寸 URL
     * @param query 搜索关键词
     * @return 图片 URL 列表（只包含 medium 尺寸）
     * @author zyh
     * @date 2025/07/15
     */
    public List<String> searchMediumImages(String query) {
        // 请求头设置（包含 API 授权信息）
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 请求参数设置（这里只设置 query，也可以加 page、per_page 等）
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        // 构造 GET 请求并执行，带上请求头和参数
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)   // 添加认证头
                .form(params)          // 添加请求参数
                .execute()             // 发送请求
                .body();               // 获取响应体

        // 解析 JSON 响应，提取 photos 数组中的 medium 尺寸 URL
        return JSONUtil.parseObj(response)            // 转为 JSONObject
                .getJSONArray("photos")               // 获取 "photos" 数组
                .stream()
                .map(photoObj -> (JSONObject) photoObj)     // 强制转换
                .map(photoObj -> photoObj.getJSONObject("src")) // 提取 src 对象
                .map(photo -> photo.getStr("medium"))         // 获取 medium 字段
                .filter(StrUtil::isNotBlank)                 // 过滤空链接
                .collect(Collectors.toList());               // 转为列表
    }
}
