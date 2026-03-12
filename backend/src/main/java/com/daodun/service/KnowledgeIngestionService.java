package com.daodun.service;

public interface KnowledgeIngestionService {

    /**
     * 解析指定 Markdown 文件并将知识条目写入数据库。
     * 若文件内容未变化（contentHash 相同），跳过入库。
     *
     * @return 入库的 chunk 数量
     */
    int ingestMarkdownFile(String filePath);

    /**
     * 扫描 sourceDir 下所有 .md 文件并逐一入库。
     *
     * @return 总入库 chunk 数量
     */
    int ingestAll();
}
