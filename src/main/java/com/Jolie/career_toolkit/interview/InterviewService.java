package com.Jolie.career_toolkit.interview;

import com.Jolie.career_toolkit.application.JobApplicationService;
import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.interview.dto.SaveInterviewRequest;
import com.Jolie.career_toolkit.interview.dto.SaveReviewRequest;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ReviewRepository reviewRepository;
    private final JobApplicationService applicationService;
    private final CurrentUser currentUser;

    public InterviewService(InterviewRepository interviewRepository,
                            ReviewRepository reviewRepository,
                            JobApplicationService applicationService,
                            CurrentUser currentUser) {
        this.interviewRepository = interviewRepository;
        this.reviewRepository = reviewRepository;
        this.applicationService = applicationService;
        this.currentUser = currentUser;
    }

    @Transactional
    public Interview create(UUID applicationId, SaveInterviewRequest request) {
        // 先讀一次 application，所有權才有被檢查到。
        // 少了這一行就可以用別人的 applicationId 幫別人新增面試。
        applicationService.get(applicationId);

        Interview interview = new Interview(applicationId, request.round(), request.scheduledAt());
        apply(interview, request);

        return interviewRepository.save(interview);
    }

    public List<Interview> listByApplication(UUID applicationId) {
        applicationService.get(applicationId);
        return interviewRepository.findByApplicationIdOrderByRoundAsc(applicationId);
    }

    /**
     * 「我未來有幾間面試」。
     *
     * 用半開區間 [now, now + days)：now 之後才算「未來」，
     * 已經過去的場次不該出現在待辦清單裡。
     */
    public List<Interview> upcoming(int days) {
        Instant now = Instant.now();
        return interviewRepository.findUpcomingByUserId(
                currentUser.id(), now, now.plus(days, ChronoUnit.DAYS));
    }

    public Interview get(UUID id) {
        return interviewRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Interview", id));
    }

    @Transactional
    public Interview update(UUID id, SaveInterviewRequest request) {
        Interview interview = get(id);
        apply(interview, request);
        if (request.scheduledAt() != null) interview.setScheduledAt(request.scheduledAt());
        if (request.status() != null) interview.setStatus(request.status());

        return interview;
    }

    // ---------- 面試檢討 ----------

    /**
     * 一場面試對應一份檢討（資料庫有 UNIQUE 約束），所以是 upsert 不是 create。
     * 使用者的心智模型是「編輯這場面試的檢討」，不是「新增第二份檢討」。
     */
    @Transactional
    public Review saveReview(UUID interviewId, SaveReviewRequest request) {
        get(interviewId);   // 所有權檢查

        Review review = reviewRepository.findByInterviewId(interviewId)
                .orElseGet(() -> new Review(interviewId));

        review.setProcessNotes(request.processNotes());
        review.setJobReality(request.jobReality());
        review.setGaps(request.gaps());
        review.setQuestionsAsked(request.questionsAsked());
        review.setRedFlags(request.redFlags());
        review.setRating(request.rating());
        review.setInterestLevel(request.interestLevel());

        return reviewRepository.save(review);
    }

    public Review getReview(UUID interviewId) {
        get(interviewId);
        return reviewRepository.findByInterviewId(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", interviewId));
    }

    /**
     * 一筆投遞底下所有場次的檢討。
     * 收到 offer 要做決策時，就是靠這個把每一輪的印象並排看。
     */
    public List<Review> reviewsForApplication(UUID applicationId) {
        applicationService.get(applicationId);
        return reviewRepository.findByApplicationId(applicationId);
    }

    private void apply(Interview interview, SaveInterviewRequest request) {
        interview.setStage(request.stage());
        interview.setFormat(request.format());
        interview.setDurationMinutes(request.durationMinutes());
        interview.setLocation(request.location());
        interview.setInterviewers(request.interviewers());
    }
}
