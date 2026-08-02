package com.Jolie.career_toolkit.interview;

import com.Jolie.career_toolkit.interview.dto.InterviewResponse;
import com.Jolie.career_toolkit.interview.dto.ReviewResponse;
import com.Jolie.career_toolkit.interview.dto.SaveInterviewRequest;
import com.Jolie.career_toolkit.interview.dto.SaveReviewRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    /** 未來 N 天的面試。這是「我未來有幾間面試」那個畫面的資料來源。 */
    @GetMapping("/interviews/upcoming")
    public List<InterviewResponse> upcoming(@RequestParam(defaultValue = "14") int days) {
        return interviewService.upcoming(days).stream().map(InterviewResponse::from).toList();
    }

    @GetMapping("/applications/{applicationId}/interviews")
    public List<InterviewResponse> listByApplication(@PathVariable UUID applicationId) {
        return interviewService.listByApplication(applicationId).stream()
                .map(InterviewResponse::from).toList();
    }

    @PostMapping("/applications/{applicationId}/interviews")
    public ResponseEntity<InterviewResponse> create(@PathVariable UUID applicationId,
                                                    @Valid @RequestBody SaveInterviewRequest request) {
        Interview interview = interviewService.create(applicationId, request);
        return ResponseEntity.status(201).body(InterviewResponse.from(interview));
    }

    @PatchMapping("/interviews/{id}")
    public InterviewResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody SaveInterviewRequest request) {
        return InterviewResponse.from(interviewService.update(id, request));
    }

    // ---------- 面試檢討 ----------

    @GetMapping("/interviews/{id}/review")
    public ReviewResponse getReview(@PathVariable UUID id) {
        return ReviewResponse.from(interviewService.getReview(id));
    }

    /**
     * 用 PUT 而不是 POST：一場面試只會有一份檢討（DB 有 UNIQUE 約束），
     * 這個操作的語意是「把這場面試的檢討設定成這樣」，重複送出結果相同。
     */
    @PutMapping("/interviews/{id}/review")
    public ReviewResponse saveReview(@PathVariable UUID id,
                                     @Valid @RequestBody SaveReviewRequest request) {
        return ReviewResponse.from(interviewService.saveReview(id, request));
    }

    /** 一筆投遞底下所有場次的檢討——收到 offer 做決策時並排看。 */
    @GetMapping("/applications/{applicationId}/reviews")
    public List<ReviewResponse> reviewsForApplication(@PathVariable UUID applicationId) {
        return interviewService.reviewsForApplication(applicationId).stream()
                .map(ReviewResponse::from).toList();
    }
}
