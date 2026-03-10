package com.daodun.service.impl;

import com.daodun.dto.PositionResponse;
import com.daodun.repository.PositionRepository;
import com.daodun.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;

    @Override
    public List<PositionResponse> listAll() {
        return positionRepository.findAllByOrderBySortOrderAscIdAsc()
                .stream()
                .map(position -> PositionResponse.builder()
                        .id(position.getId())
                        .name(position.getName())
                        .description(position.getDescription())
                        .sortOrder(position.getSortOrder())
                        .build())
                .toList();
    }
}
