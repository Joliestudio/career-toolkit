package com.Jolie.career_toolkit.offer;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "offers")
public class Offer {

    public enum Decision {
        PENDING,
        ACCEPTED,
        DECLINED,
        /** 死線過了沒回覆。跟主動婉拒是兩件事，之後回頭看自己的決策時這個差別很重要。 */
        EXPIRED
    }

    public enum SalaryPeriod { MONTHLY, ANNUAL }

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    /**
     * 金額用 BigDecimal / NUMERIC 不用 int。
     * 錢不該用整數型別扛，而且之後要換算幣別或算平均時，整數截斷會累積成明顯的誤差。
     */
    @Column(name = "base_salary", precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", nullable = false, length = 10)
    private SalaryPeriod salaryPeriod;

    /**
     * 保證年薪幾個月。
     *
     * 台灣的 offer 沒有這一欄根本沒辦法比較：月薪 50k × 14 個月 ≠ 月薪 55k × 12 個月。
     * 而比較 offer 正是這張表存在的理由。
     */
    @Column(name = "guaranteed_months", precision = 4, scale = 1)
    private BigDecimal guaranteedMonths;

    @Column(name = "bonus_note", columnDefinition = "TEXT")
    private String bonusNote;

    @Column(name = "offered_at")
    private LocalDate offeredAt;

    @Column(name = "reply_deadline")
    private LocalDate replyDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(name = "decided_at")
    private LocalDate decidedAt;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Offer() {}

    public Offer(UUID applicationId) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.currency = "TWD";
        this.salaryPeriod = SalaryPeriod.MONTHLY;
        this.decision = Decision.PENDING;
    }

    /** 換算成年薪，讓不同結構的 offer 可以放在一起比。 */
    public BigDecimal annualisedTotal() {
        if (baseSalary == null) return null;

        if (salaryPeriod == SalaryPeriod.ANNUAL) {
            return baseSalary;
        }
        // 月薪：沒有特別註明保證年薪就當作 12 個月
        BigDecimal months = guaranteedMonths != null ? guaranteedMonths : BigDecimal.valueOf(12);
        return baseSalary.multiply(months);
    }

    public void decide(Decision decision, String declineReason) {
        this.decision = decision;
        this.decidedAt = LocalDate.now();
        this.declineReason = declineReason;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public String getCurrency() { return currency; }
    public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
    public BigDecimal getGuaranteedMonths() { return guaranteedMonths; }
    public String getBonusNote() { return bonusNote; }
    public LocalDate getOfferedAt() { return offeredAt; }
    public LocalDate getReplyDeadline() { return replyDeadline; }
    public Decision getDecision() { return decision; }
    public LocalDate getDecidedAt() { return decidedAt; }
    public String getDeclineReason() { return declineReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setSalaryPeriod(SalaryPeriod salaryPeriod) { this.salaryPeriod = salaryPeriod; }
    public void setGuaranteedMonths(BigDecimal guaranteedMonths) { this.guaranteedMonths = guaranteedMonths; }
    public void setBonusNote(String bonusNote) { this.bonusNote = bonusNote; }
    public void setOfferedAt(LocalDate offeredAt) { this.offeredAt = offeredAt; }
    public void setReplyDeadline(LocalDate replyDeadline) { this.replyDeadline = replyDeadline; }
}
