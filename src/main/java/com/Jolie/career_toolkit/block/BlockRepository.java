package com.Jolie.career_toolkit.block;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface BlockRepository extends JpaRepository<Block, UUID> {
    List<Block> findByUserIdAndDeletedAtIsNull(UUID userId);
}
