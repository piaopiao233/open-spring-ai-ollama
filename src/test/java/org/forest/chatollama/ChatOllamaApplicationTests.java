package org.forest.chatollama;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;
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

    @Autowired
    private VectorStore vectorStore;
    @Test
    void test2() throws MalformedURLException {
        Resource resource = new UrlResource("http://f.coolvisit.top/d/%E6%96%87%E4%BB%B6%E4%B8%AD%E8%BD%AC%E7%AB%99/GCD/5.0%E5%B9%B3%E5%8F%B0%E6%8E%A5%E5%8F%A3%E8%A7%84%E8%8C%833.6.1(https).doc");
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);

        List<Document> documents = tikaDocumentReader.get();
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(documents);
        vectorStore.accept(splitDocuments);
    }

    @Test
    void test3() {
        List<Document> documents = vectorStore.similaritySearch("一信通的短信内容最大多少字符");
        System.out.println(documents);
    }

}
