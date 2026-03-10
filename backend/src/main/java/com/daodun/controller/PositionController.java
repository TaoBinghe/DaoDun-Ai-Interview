package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.PositionResponse;
import com.daodun.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/position")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping("/list")
    public R<List<PositionResponse>> list() {
        return R.ok(positionService.listAll());
    }
}
