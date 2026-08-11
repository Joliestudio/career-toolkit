package com.Jolie.career_toolkit.resume;

import com.Jolie.career_toolkit.resume.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeMarkdownExporter markdownExporter;

    public ResumeController(ResumeService resumeService, ResumeMarkdownExporter markdownExporter) {
        this.resumeService = resumeService;
        this.markdownExporter = markdownExporter;
    }

    @PostMapping
    public ResponseEntity<ResumeVersionResponse> create(
            @Valid @RequestBody SaveResumeVersionRequest request) {

        ResumeVersion version = resumeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(version.getId()).toUri();

        return ResponseEntity.created(location).body(ResumeVersionResponse.from(version));
    }

    @GetMapping
    public List<ResumeVersionResponse> list() {
        return resumeService.list().stream().map(ResumeVersionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResumeVersionResponse get(@PathVariable UUID id) {
        return ResumeVersionResponse.from(resumeService.get(id));
    }

    @PatchMapping("/{id}")
    public ResumeVersionResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody SaveResumeVersionRequest request) {
        return ResumeVersionResponse.from(resumeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        resumeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 鎖定：把每個積木當下的內容凍結。之後改底層積木不會再影響這份履歷。 */
    @PostMapping("/{id}/lock")
    public ResumeVersionResponse lock(@PathVariable UUID id) {
        return ResumeVersionResponse.from(resumeService.lock(id));
    }

    /** 鎖定的版本要改就複製一份，來源記在 parentId。 */
    @PostMapping("/{id}/clone")
    public ResponseEntity<ResumeVersionResponse> clone(@PathVariable UUID id,
                                                       @Valid @RequestBody SaveResumeVersionRequest request) {
        ResumeVersion copy = resumeService.clone(id, request.label());
        return ResponseEntity.status(201).body(ResumeVersionResponse.from(copy));
    }

    // ---------- 積木組裝 ----------

    @GetMapping("/{id}/blocks")
    public List<RenderedBlock> blocks(@PathVariable UUID id) {
        return resumeService.render(id);
    }

    @PostMapping("/{id}/blocks")
    public List<RenderedBlock> addBlock(@PathVariable UUID id,
                                        @Valid @RequestBody AddBlockRequest request) {
        resumeService.addBlock(id, request.blockId(), request.section());
        return resumeService.render(id);
    }

    @DeleteMapping("/{id}/blocks/{blockId}")
    public List<RenderedBlock> removeBlock(@PathVariable UUID id, @PathVariable UUID blockId) {
        resumeService.removeBlock(id, blockId);
        return resumeService.render(id);
    }

    @PutMapping("/{id}/order")
    public List<RenderedBlock> reorder(@PathVariable UUID id,
                                       @Valid @RequestBody ReorderRequest request) {
        resumeService.reorder(id, request.blockIds());
        return resumeService.render(id);
    }

    @PutMapping("/{id}/blocks/{blockId}/override")
    public List<RenderedBlock> setOverride(@PathVariable UUID id,
                                           @PathVariable UUID blockId,
                                           @RequestBody SetOverrideRequest request) {
        resumeService.setOverride(id, blockId, request.content());
        return resumeService.render(id);
    }

    // ---------- 匯出 ----------

    /**
     * Markdown 下載。
     *
     * 不做伺服器端 PDF：中文必須嵌入 CJK 字型，image 會增加 15–20MB，
     * 而排版微調比瀏覽器的「列印 → 存成 PDF」麻煩得多。
     * 列印用的排版在前端用 @media print 做，中文字型直接用使用者電腦上的。
     */
    @GetMapping(value = "/{id}/export.md", produces = "text/markdown; charset=UTF-8")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable UUID id) {
        ResumeVersion version = resumeService.get(id);
        String markdown = markdownExporter.toMarkdown(version, resumeService.render(id));

        String filename = "resume-" + version.getLabel().replaceAll("[^\\w.-]", "_") + ".md";

        return ResponseEntity.ok()
                // filename* 用 RFC 5987 的寫法，中文檔名才不會變成亂碼
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }
}
