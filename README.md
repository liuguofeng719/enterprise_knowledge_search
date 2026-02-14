# 企业级 RAG 本地知识库问答系统

本项目提供“离线入库作业 + 在线检索服务”的完整 Spring Boot 骨架，并附带 Streamlit 前端示例。

## 技术栈
- 后端：Spring Boot 3、MyBatis-Plus、H2
- RAG：LangChain4j、Chroma、Ollama
- 侧车：LlamaIndex（Python）
- 检索与优化：Lucene（全文检索）、RRF 混合检索、Cross-Encoder 重排、Caffeine 缓存、Resilience4j 并发限流
- 文档解析：PDFBox、Apache POI（DOCX）、Jsoup（HTML）
- 前端：Streamlit

## 目录结构
- `src/main/java`：后端服务与入库作业
- `samples/`：示例 Markdown 与 URL 列表
- `frontend/`：Streamlit 前端

## 前置准备
1. 启动 Chroma
```bash
pip install chromadb
chroma run --path ./chroma_data
```

2. 启动 Ollama（示例模型：llama3.1）
```bash
ollama pull llama3.1
 ollama serve
```

3. 启动 LlamaIndex 侧车服务（可选）
```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r llamaindex_service/requirements.txt
uvicorn llamaindex_service.app:app --host 0.0.0.0 --port 9001
```
如使用 Python 3.14 运行 chromadb 可能存在兼容性问题，建议使用 Python 3.11/3.12。

## 入库作业
```bash
mvn -q -DskipTests spring-boot:run \
  -Dspring-boot.run.main-class=com.example.rag.IngestApplication
```

## 上传文档入库
```bash
curl -X POST http://localhost:8080/api/ingest/upload \
  -F "files=@/path/to/demo.pdf" \
  -F "files=@/path/to/demo.md" \
  -F "version=v1" \
  -F "tags=guide,api" \
  -F "source=upload"
```
默认支持扩展名：pdf/md/markdown/txt/log/csv/docx/html/htm，可在 `rag.ingest.allowed-extensions` 中扩展。
上传文件会写入 `rag.ingest.upload-dir` 指定目录，并自动补充元数据（source/path/version/tags）。

## 启动在线检索服务
```bash
mvn -q -DskipTests spring-boot:run \
  -Dspring-boot.run.main-class=com.example.rag.RagApplication
```

## 离线评测回归
```bash
mvn -q -DskipTests spring-boot:run \
  -Dspring-boot.run.main-class=com.example.rag.EvaluationApplication \
  -Dspring-boot.run.arguments="samples/eval/eval-set.json,5"
```

## 调用接口
```bash
curl -X POST http://localhost:8080/api/qa \
  -H 'Content-Type: application/json' \
  -d '{"question":"入库流程是什么？","version":"v1","keywords":["入库","切分"]}'
```

## 启动前端
```bash
pip install streamlit requests
streamlit run frontend/app.py
```
前端包含「上传入库」「问答检索」两个标签页：
- 上传入库：支持批量上传、进度展示、失败重试与批次统计
- 问答检索：支持版本过滤与关键词增强

## 配置说明
请在 `src/main/resources/application.yml` 中调整：
- Chroma 地址与 collection
- Ollama 模型与地址
- 入库目录与切分参数
- 上传目录与允许扩展名（docx/html 解析已内置）
- 检索 TopK、minScore、候选集大小
- 混合检索（向量+全文）与全文索引配置
- 关键词重排与 Cross-Encoder 重排开关
- 查询/结果缓存、并发限流与队列容量
- 批量嵌入与评测集路径
- LlamaIndex 侧车配置（rag.llamaindex.*）：base-url、collection、top-k、timeout-ms、mode(langchain4j/llamaindex/dual)

## 文档更新记录
- 2026-02-12，执行者：人静桂花落。同步新增：docx/html 支持、上传入库前端增强、核心链路日志说明。
- 2026-02-12，执行者：Codex。同步新增：LlamaIndex 侧车服务启动说明与配置项说明。
