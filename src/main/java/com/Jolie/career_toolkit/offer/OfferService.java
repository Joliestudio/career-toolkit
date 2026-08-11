package com.Jolie.career_toolkit.offer;

import com.Jolie.career_toolkit.application.ApplicationStatus;
import com.Jolie.career_toolkit.application.JobApplicationService;
import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.offer.dto.DecideOfferRequest;
import com.Jolie.career_toolkit.offer.dto.SaveOfferRequest;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OfferService {

    private final OfferRepository offerRepository;
    private final JobApplicationService applicationService;
    private final CurrentUser currentUser;

    public OfferService(OfferRepository offerRepository,
                        JobApplicationService applicationService,
                        CurrentUser currentUser) {
        this.offerRepository = offerRepository;
        this.applicationService = applicationService;
        this.currentUser = currentUser;
    }

    /**
     * 一筆投遞只會有一個 offer（DB 有 UNIQUE 約束），所以是 upsert。
     *
     * 建立 offer 時順便把投遞狀態推進到 OFFER——
     * 「收到 offer 了但投遞狀態還停在 INTERVIEWING」是不會有人手動去修的那種不一致，
     * 而它會直接讓漏斗統計失真。
     */
    @Transactional
    public Offer save(UUID applicationId, SaveOfferRequest request) {
        var application = applicationService.get(applicationId);   // 所有權檢查

        Offer offer = offerRepository.findByApplicationId(applicationId)
                .orElseGet(() -> new Offer(applicationId));

        offer.setBaseSalary(request.baseSalary());
        if (request.currency() != null) offer.setCurrency(request.currency());
        if (request.salaryPeriod() != null) offer.setSalaryPeriod(request.salaryPeriod());
        offer.setGuaranteedMonths(request.guaranteedMonths());
        offer.setBonusNote(request.bonusNote());
        offer.setOfferedAt(request.offeredAt());
        offer.setReplyDeadline(request.replyDeadline());

        Offer saved = offerRepository.save(offer);

        if (application.getStatus().canTransitionTo(ApplicationStatus.OFFER)) {
            applicationService.changeStatus(applicationId, ApplicationStatus.OFFER, "收到 offer");
        }

        return saved;
    }

    public List<Offer> listMine() {
        return offerRepository.findAllByUserId(currentUser.id());
    }

    public Offer getByApplication(UUID applicationId) {
        applicationService.get(applicationId);
        return offerRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", applicationId));
    }

    /**
     * 接受或婉拒。
     *
     * 婉拒也要記錄——使用者明確說了「紀錄自己得到以及婉拒的 offer」。
     * 婉拒理由尤其重要：三個月後回頭看「我為什麼拒掉那家」，
     * 沒寫下來的話只剩模糊的印象。
     */
    @Transactional
    public Offer decide(UUID offerId, DecideOfferRequest request) {
        Offer offer = offerRepository.findByIdAndUserId(offerId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Offer", offerId));

        offer.decide(request.decision(), request.declineReason());

        // offer 的決定要連動投遞狀態，否則兩邊會各說各話
        ApplicationStatus target = switch (request.decision()) {
            case ACCEPTED -> ApplicationStatus.ACCEPTED;
            case DECLINED, EXPIRED -> ApplicationStatus.DECLINED;
            case PENDING -> null;
        };

        if (target != null) {
            applicationService.changeStatus(offer.getApplicationId(), target,
                    request.declineReason() != null ? request.declineReason() : "offer 決定");
        }

        return offer;
    }
}
