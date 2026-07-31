package com.Jolie.career_toolkit.block;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {
    List<Block> findByUserIdAndDeletedAtIsNull(UUID userId);
}
