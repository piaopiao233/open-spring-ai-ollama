package org.forest.chatollama;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class ChatOllamaApplicationTests {

    @Autowired
    private OllamaChatModel chatModel;

    @Test
    void contextLoads() {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .numberOfQueries(3)
                .build();
        List<Query> expand = queryExpander.expand(new Query("我和妻子总吵架怎么办"));

        System.out.println( expand);
    }

    @Test
     void test() {
        RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();

        Query transform = queryTransformer.transform(new Query("jusl是谁"));
        System.out.println( transform);
    }

}
