package com.daodun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse {

    private Long id;
    private String name;
    private String description;
    private Integer sortOrder;
}
