# Findings & Decisions
<!-- 
  WHAT: Your knowledge base for the task. Stores everything you discover and decide.
  WHY: Context windows are limited. This file is your "external memory" - persistent and unlimited.
  WHEN: Update after ANY discovery, especially after 2 view/browser/search operations (2-Action Rule).
-->

## Requirements
<!-- 
  WHAT: What the user asked for, broken down into specific requirements.
  WHY: Keeps requirements visible so you don't forget what you're building.
  WHEN: Fill this in during Phase 1 (Requirements & Discovery).
  EXAMPLE:
    - Command-line interface
    - Add tasks
    - List all tasks
    - Delete tasks
    - Python implementation
-->
<!-- Captured from user request -->
- 继续实现企业级能力：混合检索（向量+全文）、交叉重排（Cross-Encoder）、评测集与离线回归
- 性能优化：查询/结果缓存、批量嵌入、并发限流/排队、热点索引优化

## Research Findings
<!-- 
  WHAT: Key discoveries from web searches, documentation reading, or exploration.
  WHY: Multimodal content (images, browser results) doesn't persist. Write it down immediately.
  WHEN: After EVERY 2 view/browser/search operations, update this section (2-Action Rule).
  EXAMPLE:
    - Python's argparse module supports subcommands for clean CLI design
    - JSON module handles file persistence easily
    - Standard pattern: python script.py <command> [args]
-->
<!-- Key discoveries during exploration -->
- 现有项目已具备RagService/ IngestService/ KeywordReranker与测试基础
- LangChain4j支持EmbeddingStore检索与内容融合组件，可用于RRF
- Lucene 9.x 使用 lucene-analysis-common 作为分析器模块
- DJL 提供 CrossEncoderTranslator 与 HuggingFaceTokenizer 支持交叉重排

## Technical Decisions
<!-- 
  WHAT: Architecture and implementation choices you've made, with reasoning.
  WHY: You'll forget why you chose a technology or approach. This table preserves that knowledge.
  WHEN: Update whenever you make a significant technical choice.
  EXAMPLE:
    | Use JSON for storage | Simple, human-readable, built-in Python support |
    | argparse with subcommands | Clean CLI: python todo.py add "task" |
-->
<!-- Decisions made with rationale -->
| Decision | Rationale |
|----------|-----------|
| 选择Lucene做全文检索 | 生态成熟，避免自研 |
| 使用RRF融合多路检索结果 | 简洁稳定，无需训练 |
| Cross-Encoder作为可选重排 | 资源开销高，需要开关 |
| 评测回归走JUnit离线执行 | 可复现、易集成 |
| 性能优化用Caffeine与Resilience4j | 生态成熟，易配置 |
| Cross-Encoder使用DJL | 本地可部署、生态成熟 |
| Lucene分析器采用analysis-common | 9.x模块更名 |

## Issues Encountered
<!-- 
  WHAT: Problems you ran into and how you solved them.
  WHY: Similar to errors in task_plan.md, but focused on broader issues (not just code errors).
  WHEN: Document when you encounter blockers or unexpected challenges.
  EXAMPLE:
    | Empty file causes JSONDecodeError | Added explicit empty file check before json.load() |
-->
<!-- Errors and how they were resolved -->
| Issue | Resolution |
|-------|------------|
| lucene-analyzers-common 不存在 | 更换为 lucene-analysis-common |
| pytorch-native-auto 版本不匹配 | 移除依赖，保留引擎 |

## Resources
<!-- 
  WHAT: URLs, file paths, API references, documentation links you've found useful.
  WHY: Easy reference for later. Don't lose important links in context.
  WHEN: Add as you discover useful resources.
  EXAMPLE:
    - Python argparse docs: https://docs.python.org/3/library/argparse.html
    - Project structure: src/main.py, src/utils.py
-->
<!-- URLs, file paths, API references -->
- `src/main/java/com/example/rag/service/RagService.java`
- `src/main/java/com/example/rag/service/IngestService.java`
- `src/test/java/com/example/rag/retrieval/HybridRetrievalServiceTest.java`
- `src/main/java/com/example/rag/retrieval/FullTextSearchService.java`
- `src/main/java/com/example/rag/rerank/DjlCrossEncoderScoringModel.java`
- `src/main/java/com/example/rag/perf/RagCache.java`
- `src/main/java/com/example/rag/perf/QueryLimiter.java`
- `src/main/java/com/example/rag/service/BatchEmbeddingIngestor.java`
- `src/main/java/com/example/rag/EvaluationApplication.java`

## Visual/Browser Findings
<!-- 
  WHAT: Information you learned from viewing images, PDFs, or browser results.
  WHY: CRITICAL - Visual/multimodal content doesn't persist in context. Must be captured as text.
  WHEN: IMMEDIATELY after viewing images or browser results. Don't wait!
  EXAMPLE:
    - Screenshot shows login form has email and password fields
    - Browser shows API returns JSON with "status" and "data" keys
-->
<!-- CRITICAL: Update after every 2 view/browser operations -->
<!-- Multimodal content must be captured as text immediately -->
-

---
<!-- 
  REMINDER: The 2-Action Rule
  After every 2 view/browser/search operations, you MUST update this file.
  This prevents visual information from being lost when context resets.
-->
*Update this file after every 2 view/browser/search operations*
*This prevents visual information from being lost*
