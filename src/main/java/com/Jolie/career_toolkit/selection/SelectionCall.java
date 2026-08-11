package com.Jolie.career_toolkit.selection;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "selection_calls")
public class SelectionCall {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String selector;

    @Column(nullable = false, length = 40)
    private String purpose;

    /** hash(JD + 積木清單)。同樣的輸入直接回快取。 */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cost_estimate", precision = 10, scale = 6)
    private BigDecimal costEstimate;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @Column(name = "block_count", nullable = false)
    private Integer blockCount;

    @Column(name = "selected_count", nullable = false)
    private Integer selectedCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SelectionCall() {}

    public SelectionCall(UUID userId, String selector, String purpose, String requestHash,
                         int latencyMs, int blockCount, int selectedCount) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.selector = selector;
        this.purpose = purpose;
        this.requestHash = requestHash;
        this.latencyMs = latencyMs;
        this.blockCount = blockCount;
        this.selectedCount = selectedCount;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSelector() { return selector; }
    public String getPurpose() { return purpose; }
    public String getRequestHash() { return requestHash; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public BigDecimal getCostEstimate() { return costEstimate; }
    public Integer getLatencyMs() { return latencyMs; }
    public Integer getBlockCount() { return blockCount; }
    public Integer getSelectedCount() { return selectedCount; }
    public Instant getCreatedAt() { return createdAt; }
}
