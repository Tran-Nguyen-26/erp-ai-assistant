package vn.edu.uet.erpai.knowledge.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rag")
public record RagProperties(
    double minScore,
    int maxChunkTokens,
    int embeddingDimensions,
    int embeddingBatchSize,
    int candidates,
    int topK,
    String rerankerBaseUrl) {}
