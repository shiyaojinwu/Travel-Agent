package com.sz.aiagent.rag;

import com.sz.aiagent.rag.queryPre.MyMultiQueryExpander;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class MyMultiQueryExpanderTest {

    @Resource
    private MyMultiQueryExpander myMultiQueryExpander;

    @Test
    void expand() {
        List<String> queries = myMultiQueryExpander.multiQuery("啥是程序员啊啊啊啊啊啊？！请回答我哈哈哈哈");
        Assertions.assertNotNull(queries);
    }
}
