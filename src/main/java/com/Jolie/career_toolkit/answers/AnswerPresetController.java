package com.Jolie.career_toolkit.answers;

import com.Jolie.career_toolkit.answers.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AnswerPresetController {

    private final AnswerPresetService presetService;

    public AnswerPresetController(AnswerPresetService presetService) {
        this.presetService = presetService;
    }

    @GetMapping("/question-types")
    public List<QuestionTypeResponse> questionTypes() {
        return presetService.questionTypes().stream().map(QuestionTypeResponse::from).toList();
    }

    /**
     * 過去面試真正被問過的題目。
     *
     * 這是 P3 的面試檢討與 P6 的準備之間的那條線 ——
     * 沒有它，檢討就只是寫完不會再看的日記。
     */
    @GetMapping("/question-types/asked")
    public List<String> questionsAsked() {
        return presetService.questionsAskedInPastInterviews();
    }

    @PostMapping("/presets")
    public ResponseEntity<AnswerPresetResponse> create(@Valid @RequestBody SavePresetRequest request) {
        AnswerPreset preset = presetService.create(
                request.questionTypeId(), request.label(), request.targetCharLimit());
        return ResponseEntity.status(201).body(AnswerPresetResponse.from(preset));
    }

    @GetMapping("/presets")
    public List<AnswerPresetResponse> list() {
        return presetService.list().stream().map(AnswerPresetResponse::from).toList();
    }

    @PatchMapping("/presets/{id}")
    public AnswerPresetResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody SavePresetRequest request) {
        return AnswerPresetResponse.from(presetService.update(
                id, request.label(), request.targetCharLimit(), request.notes()));
    }

    @DeleteMapping("/presets/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        presetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/presets/{id}/blocks")
    public AssembledAnswer addBlock(@PathVariable UUID id,
                                    @Valid @RequestBody PresetBlockRequest request) {
        presetService.addBlock(id, request.blockId());
        return presetService.assemble(id);
    }

    @DeleteMapping("/presets/{id}/blocks/{blockId}")
    public AssembledAnswer removeBlock(@PathVariable UUID id, @PathVariable UUID blockId) {
        presetService.removeBlock(id, blockId);
        return presetService.assemble(id);
    }

    @PutMapping("/presets/{id}/order")
    public AssembledAnswer reorder(@PathVariable UUID id,
                                   @Valid @RequestBody PresetReorderRequest request) {
        presetService.reorder(id, request.blockIds());
        return presetService.assemble(id);
    }

    /** 組出可以直接複製貼進表單的答案，附上字數與是否超限。 */
    @GetMapping("/presets/{id}/assembled")
    public AssembledAnswer assembled(@PathVariable UUID id) {
        return presetService.assemble(id);
    }
}
