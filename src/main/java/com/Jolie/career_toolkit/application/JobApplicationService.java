package com.Jolie.career_toolkit.application;

import com.Jolie.career_toolkit.application.dto.CreateApplicationRequest;
import com.Jolie.career_toolkit.application.dto.UpdateApplicationRequest;
import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.resume.ResumeVersionRepository;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JobApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final CurrentUser currentUser;

    public JobApplicationService(JobApplicationRepository applicationRepository,
                                 ApplicationStatusHistoryRepository historyRepository,
                                 ResumeVersionRepository resumeVersionRepository,
                                 CurrentUser currentUser) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public JobApplication create(CreateApplicationRequest request) {
        ApplicationStatus initial = request.status() != null ? request.status() : ApplicationStatus.APPLIED;

        JobApplication application = new JobApplication(
                currentUser.id(), request.companyId(), request.positionTitle(), initial);
        application.setJobUrl(request.jobUrl());
        application.setSource(request.source());
        application.setNotes(request.notes());
        if (request.appliedAt() != null) {
            application.setAppliedAt(request.appliedAt());
        }

        JobApplication saved = applicationRepository.save(application);

        // 第一筆歷程的 fromStatus 是 null：從「不存在」到初始狀態。
        // 即使 UI 還沒要讀歷程，從第一天就要開始寫——這種資料無法事後補。
        historyRepository.save(new ApplicationStatusHistory(
                saved.getId(), null, initial, "建立投遞紀錄"));

        return saved;
    }

    public List<JobApplication> list(ApplicationStatus status) {
        UUID userId = currentUser.id();

        return status == null
                ? applicationRepository.findByUserIdAndDeletedAtIsNullOrderByStatusChangedAtDesc(userId)
                : applicationRepository
                        .findByUserIdAndStatusAndDeletedAtIsNullOrderByStatusChangedAtDesc(userId, status);
    }

    public JobApplication get(UUID id) {
        return applicationRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Application", id));
    }

    public List<ApplicationStatusHistory> statusHistory(UUID applicationId) {
        // 先跑一次 get()，所有權才有被檢查到。
        // 少了這一行就可以用別人的 applicationId 讀出別人的歷程。
        get(applicationId);
        return historyRepository.findByApplicationIdOrderByChangedAtAsc(applicationId);
    }

    @Transactional
    public JobApplication update(UUID id, UpdateApplicationRequest request) {
        JobApplication application = get(id);

        // 部分更新：null 代表「不要動」，不是「清空」
        if (request.positionTitle() != null) application.setPositionTitle(request.positionTitle());
        if (request.jobUrl() != null) application.setJobUrl(request.jobUrl());
        if (request.source() != null) application.setSource(request.source());
        if (request.appliedAt() != null) application.setAppliedAt(request.appliedAt());
        if (request.nextActionAt() != null) application.setNextActionAt(request.nextActionAt());
        if (request.notes() != null) application.setNotes(request.notes());

        if (request.resumeVersionId() != null) {
            // 先確認那份履歷是自己的，查不到就 404。
            // 資料庫的複合外鍵 (user_id, resume_version_id) 是第二道防線——
            // 就算這裡的檢查被誰刪掉，也不可能掛到別人的履歷版本。
            resumeVersionRepository
                    .findByIdAndUserIdAndDeletedAtIsNull(request.resumeVersionId(), currentUser.id())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ResumeVersion", request.resumeVersionId()));

            application.setResumeVersionId(request.resumeVersionId());
        }

        return application;
    }

    /**
     * 狀態轉換。這是整個 P3 的核心。
     *
     * 檢查寫在 Service 而不是資料庫：
     *   - DB 的 CHECK 約束看不到「前一個值」，沒辦法表達「從 REJECTED 不能跳到 OFFER」
     *   - trigger 做得到，但那會把商業邏輯藏在你永遠不會去找的地方
     *
     * 每一次成功的轉換都寫一筆歷程。這件事必須跟狀態變更在同一個交易裡，
     * 否則就會出現「狀態改了但沒有紀錄」的資料，而且事後分不出來是漏寫還是本來就沒改過。
     */
    @Transactional
    public JobApplication changeStatus(UUID id, ApplicationStatus target, String note) {
        JobApplication application = get(id);
        ApplicationStatus current = application.getStatus();

        if (current == target) {
            return application;   // 沒有變化就不寫歷程，避免歷程被同一個狀態洗版
        }
        if (!current.canTransitionTo(target)) {
            throw new IllegalStatusTransitionException(current, target);
        }

        application.applyStatus(target);
        historyRepository.save(new ApplicationStatusHistory(id, current, target, note));

        return application;
    }

    @Transactional
    public void delete(UUID id) {
        get(id).markAsDeleted();
    }
}
