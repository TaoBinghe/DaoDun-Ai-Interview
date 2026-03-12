package com.daodun.service;

import com.daodun.entity.KnowledgeChunk;

import java.util.List;

public interface KnowledgeRetrievalService {

    /**
     * 混合检索：向量召回 + 关键词兜底，按岗位范围过滤。
     *
     * @param positionName 岗位名称
     * @param queryText    查询文本（一般由当前题目 + 候选人回答拼接而成）
     * @param keywords     额外关键词列表，用于规则兜底召回
     * @return 去重排序后的 Top-K 知识条目
     */
    List<KnowledgeChunk> retrieve(String positionName, String queryText, List<String> keywords);
}
