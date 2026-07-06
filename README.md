# chat-ollama

一个干净、简单、偏实战的 AI 聊天后端项目。  
基于 `Spring Boot 4` + `Spring AI 2.0` + `Ollama` + `MariaDB` + `Qdrant` 构建，支持流式聊天、多轮对话落库、工具调用、RAG 检索增强以及查询变体召回。

---
<h2>前端项目：<a href="https://gitee.com/jusenlin/chat-web">chat-web</a></h2>

## 1. 项目简介

这个项目主要用于搭建一个本地可控、易扩展的聊天服务后端，当前已经具备以下能力：

- 支持 **SSE 流式输出**，便于前端实时展示模型回复
- 支持 **多轮对话上下文**，可基于 `sessionId` 持续对话
- 支持 **聊天记录落库**，会话和消息都会保存到数据库
- 支持 **工具调用（Tool Calling）和网络搜索**，方便扩展查询、执行类能力，目前支持**网络搜索工具（Tavily）**
- 支持 **RAG 检索增强生成**
- 支持 **RAG 查询变体生成**，提升知识检索召回效果

如果你想找一个结构不复杂、方便二次开发、又能覆盖聊天 + 工具调用 + 知识库检索能力的 Java 后端项目，这个仓库比较适合拿来继续扩展。

---

## 2. 核心特性

### 干净简单

项目整体结构比较直接，核心代码围绕以下几部分展开：

- `controller`：对外提供聊天接口
- `service`：处理会话、消息、工具调用、RAG 流程
- `model / dto`：请求与数据模型定义
- `mapper`：数据库访问
- `util`：RAG 相关辅助工具

适合用来：

- 学习 Spring AI + Ollama 的基础接入方式
- 做一个本地知识库聊天后端
- 在现有项目里扩展 AI 对话能力

### 多轮对话可落数据库

项目支持会话与消息持久化，当前已包含两张基础表：

- `chat_session`：保存会话信息
- `chat_message`：保存聊天消息内容、记录类型、工具相关元数据等

也就是说，这不是一个“只回一次话”的 Demo，而是支持真正多轮对话上下文回放的后端服务。

### 支持工具调用

项目中已经接入工具调用能力，模型在对话过程中可以按配置触发工具，并将工具执行相关信息纳入整体会话流程中，便于后续扩展：

- 查询类工具
- 系统能力类工具
- 业务接口类工具
- **网络搜索工具（Tavily）**

工具调用流程基于 Spring AI 2.0 的 `ToolCallingAdvisor`，项目通过 `LoggingToolCallingManager` 装饰默认 `ToolCallingManager`，在工具执行前后把工具调用请求和工具执行结果写入 `chat_message`。本轮会话的 `sessionId`、`recordId` 会通过 `ToolCallingChatOptions.toolContext` 传递，便于后续扩展用户、租户、权限隔离等业务上下文。

这类能力特别适合后续做“AI 调系统”“AI 调接口”“AI 查业务数据”场景。

#### 网络搜索工具

项目集成了 [Tavily](https://tavily.com/) 网络搜索引擎，AI 模型可通过 Tool Calling 实时搜索互联网获取最新资讯。

配置方式：

```yml
tavily:
  api-key: your-api-key
  max-results: 10
```
### 支持 RAG 与查询变体

项目不仅支持向量知识库检索，还支持将用户问题扩展为多个查询变体，再进行召回，这种方式通常能比单次原问题检索获得更好的匹配结果。

适合场景：

- 企业知识库问答
- 文档问答
- FAQ 检索增强
- 多表达方式下的语义召回

---

## 3. 技术栈

- `Java 21`
- `Spring Boot 4.1.x`
- `Spring AI 2.0.x`
- `Ollama`
- `MariaDB`
- `MyBatis-Plus`
- `Qdrant`
- `Hutool`
- `Reactor Flux`

---

## 4. 运行环境要求

在启动项目前，建议先准备以下环境：

- `JDK 21`
- `Maven 3.9+`
- `MariaDB`
- `Ollama`
- `Qdrant`（可选）

默认服务端口：

- `8888`

---

## 5. 依赖组件说明

### Ollama

项目默认通过 `Ollama` 提供大模型聊天与向量 embedding 能力。  
你需要先在本地或服务器上启动 Ollama 服务，并准备好对应模型。

当前配置示例中可见：

- 聊天模型：`minimax-m2.5:cloud`
- 向量模型：`nomic-embed-text`

请根据你的实际环境替换为可用模型名称。

### MariaDB

项目使用 `MariaDB` 存储会话与消息记录。  
多轮对话是否能延续、历史消息是否能回放，依赖数据库中的会话与消息数据。

### Qdrant（向量知识库）

`Qdrant` 在这个项目里承担 **向量知识库** 的角色，主要用于：

- 文档向量存储
- 语义检索
- RAG 知识增强
- 查询变体后的多路召回

**如果你暂时不使用知识库 / RAG 功能，请把 `application-dev.yml` 中的 `spring.ai.vectorstore.qdrant` 相关配置注释掉。**

否则在某些环境下，项目启动时会因为无法连接 `Qdrant` 而启动失败。

也就是说：

- 只想体验基础聊天：可以不启用 `Qdrant`
- 想使用知识库检索与 RAG：需要启动 `Qdrant`

---

## 6. 配置说明

主要配置文件：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

建议重点关注以下配置项：

### 数据库配置

```yml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/你的库名
    username: 你的用户名
    password: 你的密码
```

### Ollama 配置

```yml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: 你的聊天模型
      embedding:
        model: 你的向量模型
```

### Qdrant 配置

```yml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: my-rag
        initialize-schema: true
        use-tls: false
        api-key:
```

### RAG 参数配置

项目还提供了几个和召回效果相关的参数：

```yml
system:
  ai:
    topKPerQuery: 5
    allTopK: 20
    numVariants: 5
    similarityThreshold: 0.65
```

这些参数分别用于控制：

- 每个查询变体召回多少文档
- 所有查询变体合并后的总召回数量
- 生成多少个查询变体
- 向量检索相似度阈值

---

## 7. 数据库初始化

项目已提供基础 SQL：

- `sql/base.sql`

执行后会创建以下表：

- `chat_session`
- `chat_message`

这两张表分别用于保存：

- 会话信息
- 会话中的用户消息、AI 回复、工具消息等

---

## 8. 启动步骤

### 方式一：只体验基础聊天

1. 准备并启动 `MariaDB`
2. 执行 `sql/base.sql`
3. 准备并启动 `Ollama`
4. 修改 `application-dev.yml` 中的数据库和模型配置
5. **注释掉 `spring.ai.vectorstore.qdrant` 相关配置**
6. 启动项目

### 方式二：体验完整 RAG / 知识库能力

1. 准备并启动 `MariaDB`
2. 执行 `sql/base.sql`
3. 准备并启动 `Ollama`
4. 准备并启动 `Qdrant`
5. 修改 `application-dev.yml` 中数据库、Ollama、Qdrant 配置
6. 启动项目

启动成功后，默认访问地址可按本地端口 `8888` 进行调用。

---

## 9. 核心接口说明

## `POST /ChatMessage/message`

### 接口作用

这是项目最核心的聊天接口，用于发起或继续一个多轮会话，并以 **SSE 流式** 方式返回模型输出。

### 接口特点

- 支持新建会话
- 支持基于已有 `sessionId` 继续多轮对话
- 用户消息会先落库
- AI 最终完整回复会在流结束后落库
- 可结合工具调用参与对话过程

### 请求说明

请求体为 JSON，核心字段通常包括：

- `sessionId`：会话 ID，可为空；为空时系统自动创建新会话
- `message`：用户输入的问题

### 请求示例

```http
POST /ChatMessage/message
Content-Type: application/json
Accept: text/event-stream

{
  "sessionId": "",
  "message": "请帮我总结一下这份知识库文档的重点"
}
```

### 返回说明

返回为 `text/event-stream` 流式结果，前端可以边接收边展示。  
如果是新会话，响应中也会带回对应的会话标识信息，方便继续追问。

---

## `POST /ChatMessage/multiQuerySimilaritySearch`

### 接口作用

这个接口用于做 **RAG 检索前的查询扩展与向量召回**。

它会根据：

- 当前问题
- 可选的历史上下文会话

先生成多个查询变体，然后再到向量知识库中执行相似度检索，最终返回匹配到的文档列表。

### 接口特点

- 支持结合历史会话上下文理解当前问题
- 支持生成多个查询变体
- 支持提升复杂问题、模糊表达场景下的召回效果
- 依赖 `Qdrant` 可用

### 请求参数

- `sessionId`：可选，会话 ID；传入后可结合上下文生成更合适的查询变体
- `currentQuestion`：当前用户问题，必填

### 请求示例

```http
POST /ChatMessage/multiQuerySimilaritySearch?sessionId=你的会话ID&currentQuestion=系统如何支持多轮对话
```

### 使用前提

此接口依赖：

- `Qdrant` 已启动
- 向量库配置正确
- 已有可被检索的知识内容

如果你没有启用向量知识库，这个接口可以先不使用。

---

## 10. 其他接口

### `GET /ChatMessage/simpleMessage`

简化版流式聊天接口，适合快速测试模型输出。

### `GET /ChatMessage/selectBySessionId`

根据会话 ID 查询历史消息，便于前端展示聊天记录。

---

## 11. 项目目录

```text
src/main/java/org/forest/chatollama
├─ config          # 配置类
├─ controller      # 接口层
├─ dto             # 请求/响应对象
├─ mapper          # MyBatis-Plus 数据访问
├─ model           # 数据模型
├─ service         # 业务逻辑、工具调用、会话处理
│  └─ ai           # AI相关工具调用（网络搜索等）
├─ util            # 工具类
│  └─ websearch    # 网络搜索工具（Tavily等）
└─ common          # 通用异常和常量
```

---

## 12. 适合扩展的方向

如果你准备基于这个项目继续开发，后续可以考虑扩展：

- 前端聊天页面
- 文件上传与知识库导入
- 更多 Tool Calling 工具
- 多模型切换
- Prompt 管理
- 用户权限与会话隔离
- OpenAPI / Swagger 文档完善

---
