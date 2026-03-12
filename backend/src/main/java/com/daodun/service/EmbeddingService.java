package com.daodun.service;

import java.util.List;

public interface EmbeddingService {

    /**
     * 对单段文本生成 embedding 向量。
     *
     * @return 浮点数组形式的向量
     */
    float[] embed(String text);

    /**
     * 批量生成 embedding。
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 将 float[] 转为 pgvector 可接受的字符串格式 "[0.1,0.2,...]"
     */
    static String toVectorString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
