package com.sz.aiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ✅ WebSearchTool：网页搜索工具 使用 SearchAPI 平台接入百度搜索引擎，执行关键词检索，供 AI 进行工具调用。 支持自动拼接请求参数、解析响应结果并返回前 5 条内容。
 * 👉 适用于 Spring AI 工具系统（配合 @Tool 和 @ToolParam 使用）
 *
 * @author zyh
 * @version 1.0.0
 * @date 2025/07/13
 */
@Component
@Slf4j
public class WebSearchTool {

  /** SearchAPI 提供的搜索接口地址（支持 engine=baidu） */
  private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

  /** 当前工具使用的 API 密钥（每个实例独立传入） */
  private final String apiKey;

  /** 构造函数，注入 API Key（由配置文件中读取） */
  public WebSearchTool(@Value("${search-api.api-key}") String apiKey) {
    this.apiKey = apiKey;
  }

  /**
   * 🌐 搜索网页信息（Baidu） 被 AI 模型调用时，Spring AI 会根据 @Tool 和 @ToolParam 自动识别工具与参数。
   *
   * @param query 搜索关键词（如 “广州天气”）
   * @return 返回前 5 条搜索结果的 JSON 字符串
   * @author zyh
   * @date 2025/07/13
   */
  @Tool(description = "使用百度搜索引擎查找相关信息")
  public String searchWeb(@ToolParam(description = "要搜索的关键词") String query) {

    // 构造请求参数
    Map<String, Object> paramMap = new HashMap<>();
    // 搜索关键词
    paramMap.put("q", query);
    // API 密钥
    paramMap.put("api_key", apiKey);
    // 使用百度搜索引擎
    paramMap.put("engine", "baidu");
    try {
      // 发送 GET 请求并获取返回结果
      String response = HttpUtil.get(SEARCH_API_URL, paramMap, 15000);
      // 将响应内容转换为 JSON 对象
      JSONObject jsonObject = JSONUtil.parseObj(response);
      // 取出 organic_results 部分（即自然搜索结果）
      JSONArray organicResults = jsonObject.getJSONArray("organic_results");
      // 提取前 5 条结果
      if (organicResults == null || organicResults.isEmpty()) return "未检索到相关信息。";
      List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
      // 将每条结果转为字符串并拼接
      log.debug("Search returned {} results", objects.size());
      return objects.stream()
          .map(
              obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
              })
          .collect(Collectors.joining(","));
    } catch (Exception e) {
      // 异常处理，防止接口请求失败导致模型报错
      log.warn("Search failed: {}", e.getClass().getSimpleName());
      return "搜索服务暂时不可用，请不要据此编造搜索结果。";
    }
  }
}
