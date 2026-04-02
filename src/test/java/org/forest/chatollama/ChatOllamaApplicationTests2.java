package org.forest.chatollama;

import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.util.SpringAiRagUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootTest
class ChatOllamaApplicationTests2 {

    @Autowired
    private OllamaChatModel chatModel;

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    SpringAiRagUtils springAiRagUtils;


    @Test
    void contextLoads() {
        ChatOptions chatOptions = OllamaChatOptions.builder()
                .disableThinking()
                .build();
        ChatClient.Builder builder = ChatClient.builder(chatModel).defaultOptions(chatOptions);
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(builder)
                .numberOfQueries(3)
                .build();
        List<Query> expand = queryExpander.expand(new Query("http调用方式是什么？"));
        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        List<Document> allDocs = expand.stream()
                .flatMap(q -> retriever.retrieve(q).stream())   // 核心：向量搜索
                .toList();

        System.out.println(111);


    }

    @Test
    void testGenerateStream() {
        List<ChatMessage> list = chatMessageService.list();
        System.out.println(list);
    }

    @Test
    void test() {
        RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();

        Query transform = queryTransformer.transform(new Query("成龙是谁"));
        System.out.println(transform);
    }

    @Autowired
    private VectorStore vectorStore;


    @Autowired
    private JdbcTemplate jdbcTemplate;


    @Autowired
    private EmbeddingModel embeddingModel;




    @Test
    public void ingestWordDocument() throws MalformedURLException {
        // 1. 加载 Word 文件（本地路径）
        String fileUrl = "";
        Resource resource = new UrlResource(fileUrl);
        // 2. 使用 TikaDocumentReader 读取（自动提取文本，保留基本结构）
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        // reader.read() 返回 List<Document>，每个 Document 可能对应整篇或按段落/页
        List<Document> documents = reader.read();
        // 3. 可选：切分成小块（强烈推荐，尤其是长文档）
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);
        // 4. 添加到向量数据库（自动 embedding + 存储）
        for (Document doc : chunks) {
            doc.getMetadata().put("source_url", fileUrl);
        }
        vectorStore.add(chunks);
        System.out.println("已存入 " + chunks.size() + " 个 chunk 到向量数据库");
    }


    @Test
    void shouldAddAndSearchSimilarDocuments() {
        // 准备测试数据
        List<Document> documents = List.of(
                new Document("Spring AI 支持 Qdrant 作为向量存储", Map.of("category", "tech")),
                new Document("Qdrant 是 Rust 实现的向量数据库", Map.of("category", "vector-db")),
                new Document("今天新加坡天气很热", Map.of("category", "weather"))
        );
        // 添加
        vectorStore.add(documents);
    }

    @Test
    void test4() {
        SearchRequest request = SearchRequest.builder().query("次查询接口返回的最大回复信息是多少 ？")
                .topK(5)
                .similarityThreshold(0.45f)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        System.out.println(111);
    }


    /**
     * rag关键词变体多路查询
     */
    @Test
    void test6() {
        var historyMessages = new ArrayList<Message>();
        historyMessages.add(new UserMessage("""
                找一部香港老电影，剧情开始里面一堆和尚在捉拿一个人，这个人有个特异功能，就是一只手可以飞出来，把这些和尚打死了
                """));
        historyMessages.add(new AssistantMessage("""
                你要找的应该是1980 年的香港邵氏经典恐怖片《邪》。
                 剧情匹配点
                 开头就是一群和尚上门捉拿女鬼，女鬼有断手飞出攻击的特异能力。
                 和尚砍掉女鬼的手后，断手自己爬动、飞出去，把和尚们打死 / 吓疯，完全符合你描述的 “一只手可以飞出来，把这些和尚打死了”。
                """));
        var currentQuestion = "不对，其中还有各大门派一起抓他的剧情";
        Long startTime = System.currentTimeMillis();
        List<String> strings = springAiRagUtils.generateMultiCondensedQueries(historyMessages, currentQuestion);
        Long endTime = System.currentTimeMillis();
        System.out.println("耗时：" + (endTime - startTime) + "ms");
        System.out.println(strings);

    }

}
