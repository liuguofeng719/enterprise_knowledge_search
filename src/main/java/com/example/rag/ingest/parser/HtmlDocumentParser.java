package com.example.rag.ingest.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// 基于 Jsoup 的 HTML 解析器
public class HtmlDocumentParser implements DocumentParser {

    @Override
    public Document parse(InputStream inputStream) {
        try {
            org.jsoup.nodes.Document html = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
            html.select("script,style,noscript").remove();
            String text = html.text();
            return Document.from(text, new Metadata());
        } catch (IOException e) {
            throw new IllegalStateException("解析 HTML 失败", e);
        }
    }
}
