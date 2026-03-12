package com.daodun.dto;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeVerifyRequest {

    /** 岗位名称，与知识库 position_name 一致，如 "Java后端开发" */
    private String positionName;
    /** 检索查询文本，模拟面试中的问题+回答拼接 */
    private String queryText;
    /** 可选关键词列表，用于混合检索的关键词一路 */
    private List<String> keywords;
}
