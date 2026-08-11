package com.Jolie.career_toolkit.parsing;

import com.Jolie.career_toolkit.parsing.dto.CandidateResponse;
import com.Jolie.career_toolkit.parsing.dto.ResumeFileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resume-files")
public class ResumeFileController {

    private final ResumeFileService resumeFileService;

    public ResumeFileController(ResumeFileService resumeFileService) {
        this.resumeFileService = resumeFileService;
    }

    @PostMapping
    public ResponseEntity<ResumeFileResponse> upload(@RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("讀取上傳檔案失敗", e);
        }

        ResumeFile saved = resumeFileService.upload(file.getOriginalFilename(), content);
        return ResponseEntity.status(201).body(ResumeFileResponse.from(saved));
    }

    @GetMapping
    public List<ResumeFileResponse> list() {
        return resumeFileService.list().stream().map(ResumeFileResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResumeFileResponse get(@PathVariable UUID id) {
        return ResumeFileResponse.from(resumeFileService.get(id));
    }

    @GetMapping("/{id}/candidates")
    public List<CandidateResponse> candidates(@PathVariable UUID id) {
        return resumeFileService.candidates(id).stream().map(CandidateResponse::from).toList();
    }

    /** 接受候選 → 產生一個積木（內容留空給使用者自己寫）。 */
    @PostMapping("/candidates/{candidateId}/accept")
    public CandidateResponse accept(@PathVariable UUID candidateId) {
        return CandidateResponse.from(resumeFileService.accept(candidateId));
    }

    @PostMapping("/candidates/{candidateId}/reject")
    public CandidateResponse reject(@PathVariable UUID candidateId) {
        return CandidateResponse.from(resumeFileService.reject(candidateId));
    }
}
