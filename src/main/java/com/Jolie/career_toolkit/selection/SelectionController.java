package com.Jolie.career_toolkit.selection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/selection")
public class SelectionController {

    private final SelectionService selectionService;

    public SelectionController(SelectionService selectionService) {
        this.selectionService = selectionService;
    }

    /**
     * JD 上限 20000 字。
     *
     * 現在的選擇器零成本，這個限制的作用是擋住「整個網頁貼進來」的情況。
     * 接上 LLM 之後它會直接變成成本控制——輸入長度就是錢。
     */
    public record SelectRequest(@NotBlank @Size(max = 20_000) String jobDescription) {}

    @PostMapping
    public SelectionResult select(@Valid @RequestBody SelectRequest request) {
        return selectionService.selectForJobDescription(request.jobDescription());
    }
}
