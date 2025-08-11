package com.sz.aiagentmcp;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ImageSearchToolTest {
  @Test
  void missingCredentialFailsBeforeNetworkRequest() {
    var tool = new ImageSearchTool();
    assertThatThrownBy(() -> tool.searchMediumImages("深圳"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PEXELS_API_KEY");
    assertThat(tool.searchImage("深圳")).isEqualTo("图片搜索不可用，请检查服务配置。");
  }
}
