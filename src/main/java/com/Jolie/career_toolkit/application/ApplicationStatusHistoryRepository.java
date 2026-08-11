package com.Jolie.career_toolkit.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 歷程是掛在 application 底下的，所有權已經在讀取 application 時檢查過。
 * 這裡的方法名沒有 UserId，EXEMPT 清單裡有寫明理由。
 */
public interface ApplicationStatusHistoryRepository
        extends JpaRepository<ApplicationStatusHistory, UUID> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}
