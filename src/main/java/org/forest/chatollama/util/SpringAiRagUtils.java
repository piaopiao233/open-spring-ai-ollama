package org.forest.chatollama.util;

import org.forest.chatollama.config.AiSystemConfig;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class SpringAiRagUtils {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final AiSystemConfig aiSystemConfig;

    public SpringAiRagUtils(ChatModel chatModel, VectorStore vectorStore,AiSystemConfig aiSystemConfig) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.aiSystemConfig = aiSystemConfig;
    }

    /**
     * 核心方法：把历史消息 + 当前问题 转成多个独立的 condensedQuery（多变体）
     *
     * @param historyMessages 历史消息列表（不包含当前用户消息）
     * @param currentQuestion 当前用户问题
     * @return List<String> 查询列表（第1个一定是原始问题）
     */
    public List<String> generateMultiCondensedQueries(List<Message> historyMessages,
                                                      String currentQuestion) {

        // 构建 Prompt，让 LLM 输出「每行一个查询」
        List<Message> condenseMessages = new ArrayList<>();
        condenseMessages.add(new SystemMessage(getQueryVariantPrompt(aiSystemConfig.getNumVariants())));
        if (!historyMessages.isEmpty()){
            condenseMessages.addAll(historyMessages);
        }
        condenseMessages.add(new UserMessage("用户跟进问题：" + currentQuestion));
        //关闭思考
        ChatOptions chatOptions = OllamaChatOptions.builder()
                .disableThinking()
                .build();
        Prompt prompt = new Prompt(condenseMessages, chatOptions);
        String text = chatModel.call(prompt).getResult().getOutput().getText().trim();
        // 按行解析 + 清理
        List<String> queries = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        // 限制数量
        return queries.stream().limit(aiSystemConfig.getNumVariants()).toList();
    }


    public static String getQueryVariantPrompt(int numVariants) {
        return String.format("""
                你是一个专业的RAG检索关键词或者关键句生成助手。
                根据完整的对话历史和用户的跟进问题，生成 %d 个独立的、包含所有必要上下文的RAG搜索关键词或者关键句/查询变体。
                        
                核心要求：
                1. 每个关键词或者关键句/查询必须独占一行
                2. 每个变体都必须基于上下文补充完整信息（解决指代、省略、歧义等问题）
                3. 每个变体都是独立可用于RAG检索的精准关键词/关键句，而非完整问句
                4. 不要编号、不要解释、不要引号、不要多余文字
                5. 语言必须和用户问题一致
                                                
                示例输出：
                带上下文的精准检索关键词或者句子1
                带上下文的精准检索关键词或者句子2
                带上下文的精准检索关键词或者句子3
                """, numVariants);
    }

    /**
     * 多查询并行检索 + 自动去重
     *
     * @param queries      多个查询变体
     * @return 合并去重后的 Document 列表
     */
    public List<Document> multiQuerySimilaritySearch(List<String> queries) {
        // 使用 document ID 去重 + 按 score 降序 + 最终限制数量
        Map<String, DocumentWithScore> bestById = new HashMap<>();

        for (String query : queries) {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(aiSystemConfig.getTopKPerQuery())
                    .similarityThreshold(aiSystemConfig.getSimilarityThreshold())
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);

            for (Document doc : docs) {
                String docId = doc.getId();
                Double score = doc.getScore();
                DocumentWithScore candidate = new DocumentWithScore(doc, score);

                // 保留 score 更高的那个
                bestById.compute(docId, (key, existing) -> {
                    if (existing == null || candidate.score > existing.score) {
                        return candidate;
                    }
                    return existing;
                });
            }
        }
        // 按最高 score 降序排序
        return bestById.values().stream()
                .sorted(Comparator.comparingDouble((DocumentWithScore d) -> d.score).reversed())
                .limit(aiSystemConfig.getAllTopK())   // 最终限制数量，可调，例如最多 20 条
                .map(DocumentWithScore::document)
                .collect(Collectors.toList());
    }



    /**
     * 把 List<Document> 转成带分隔符的上下文字符串（推荐写法）
     */
    public String buildRagContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        AtomicInteger index = new AtomicInteger(1);
        String ragContent = documents.stream()
                .map(doc -> {
                    // 获取当前序号，并自增
                    int seq = index.getAndIncrement();
                    // 获取来源，默认值调整为更清晰的表述
                    String source = (String) doc.getMetadata().getOrDefault("source", "未命名知识库片段");
                    // 拼接带编号的来源和文本内容
                    return "【知识库文档来源编号" + seq + "：" + source + "】\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
        ragContent = """
                用户已检索以下知识库文档，请结合文档内容回答问题，优先使用文档信息，如果用户的提问与文档无关或者文档无相关内容，你就忽略知识库文档信息。
                """ + "\r\n" + ragContent;
        return ragContent;
    }



    private record DocumentWithScore(Document document, Double score) {}
}
