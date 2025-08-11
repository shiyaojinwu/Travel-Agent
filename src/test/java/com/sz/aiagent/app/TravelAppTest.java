package com.sz.aiagent.app;

import jakarta.annotation.Resource;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
class TravelAppTest {

  @Resource private TravelApp travelApp;

  @Test
  void testChat() {
    // String chatId = UUID.randomUUID().toString();
    // 第一轮
    String message = "你好，我是张三";
    travelApp.doChat(message, String.valueOf(0));
    // 第二轮
    message = "我想去广州玩";
    travelApp.doChat(message, String.valueOf(0));
    // 第三轮
    message = "我要去哪玩来着？刚跟你说过，帮我回忆一下";
    travelApp.doChat(message, String.valueOf(0));
  }

  @Test
  void doChatWithReport() {
    String chatId = UUID.randomUUID().toString();
    String message = "你好，我是程序员张三，我要去广州旅游";
    TravelApp.TravelReport travelReport = travelApp.doChatWithReport(message, chatId);
    Assertions.assertNotNull(travelReport);
  }

  @Test
  void doChatWithRag() {
    String chatId = UUID.randomUUID().toString();
    String message = "与伴侣家人同行旅行，发生分歧怎么办？";
    String answer = travelApp.doChatWithRag(message, chatId);
    Assertions.assertNotNull(answer);
  }

  @Test
  void doChatWithTools() {
    // 测试联网搜索问题的答案
    testMessage("周末想带女朋友去广州，推荐几个适合情侣的小众打卡地？");

    // 测试网页抓取：恋爱案例分析
    testMessage("看看www.bilibili.com网站里有什么");

    // 测试资源下载：图片下载
    testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");

    // 测试终端操作：执行代码
    testMessage("执行 ping www.bilibili.com,看看能否ping得通");

    // 测试文件操作：保存用户档案
    testMessage("保存我的游玩档案为文件");

    // 测试 PDF 生成
    testMessage("生成一份‘七夕出行计划’PDF，包含餐厅预订、活动流程和礼物清单");
  }

  private void testMessage(String message) {
    String answer = travelApp.doChatWithTools(message, String.valueOf(0));
    Assertions.assertNotNull(answer);
  }

  @Test
  void doChatWithMcp() {
    String chatId = UUID.randomUUID().toString();
    // 测试地图 MCP
    //        String message = "我的另一半居住在广州番禺区大学城，请帮我找到 5 公里内合适的约会地点,如果你调用了什么工具要告诉我";
    //        String answer =  travelApp.doChatWithMcp(message, chatId);
    //        Assertions.assertNotNull(answer);
    // 测试图片搜索 MCP
    String message = "帮我搜索一些广州大学城的图片";
    String answer = travelApp.doChatWithMcp(message, chatId);
    Assertions.assertNotNull(answer);
  }
}
