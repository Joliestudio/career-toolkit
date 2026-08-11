package com.Jolie.career_toolkit.parsing;

import com.Jolie.career_toolkit.block.Block;
import com.Jolie.career_toolkit.block.BlockRepository;
import com.Jolie.career_toolkit.block.BlockType;
import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.storage.FileStorage;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ResumeFileService {

    /**
     * 允許的類型，用 magic byte 判斷出來的結果去比對。
     * 副檔名與 Content-Type header 都是使用者送什麼就是什麼，一律不信。
     */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "application/pdf", "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/msword", "doc",
            "text/plain", "txt",
            "application/rtf", "rtf"
    );

    private final ResumeFileRepository fileRepository;
    private final ExtractionCandidateRepository candidateRepository;
    private final BlockRepository blockRepository;
    private final FileStorage storage;
    private final TextExtractor textExtractor;
    private final DictionaryExtractor dictionaryExtractor;
    private final CurrentUser currentUser;

    public ResumeFileService(ResumeFileRepository fileRepository,
                             ExtractionCandidateRepository candidateRepository,
                             BlockRepository blockRepository,
                             FileStorage storage,
                             TextExtractor textExtractor,
                             DictionaryExtractor dictionaryExtractor,
                             CurrentUser currentUser) {
        this.fileRepository = fileRepository;
        this.candidateRepository = candidateRepository;
        this.blockRepository = blockRepository;
        this.storage = storage;
        this.textExtractor = textExtractor;
        this.dictionaryExtractor = dictionaryExtractor;
        this.currentUser = currentUser;
    }

    @Transactional
    public ResumeFile upload(String originalName, byte[] content) {
        UUID userId = currentUser.id();

        // 用內容判斷類型，不看副檔名。把 .exe 改名成 .pdf 是最基本的攻擊手法。
        String detected = textExtractor.detectContentType(content, originalName);
        String extension = ALLOWED_TYPES.get(detected);
        if (extension == null) {
            throw new UnsupportedFileTypeException(detected);
        }

        String sha256 = sha256(content);

        // 同一個人重複上傳同一份檔案 → 直接回既有那筆，不要堆出重複的候選
        var existing = fileRepository.findByUserIdAndSha256(userId, sha256);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 儲存 key 由伺服器產生，跟使用者的檔名完全無關（路徑穿越）
        String storageKey = userId + "/" + UUID.randomUUID() + "." + extension;
        storage.store(storageKey, content);

        ResumeFile file = fileRepository.save(new ResumeFile(
                userId, originalName, detected, content.length, sha256, storageKey));

        parseAndExtract(file, content);
        return file;
    }

    /**
     * 抽文字 → 抽候選。
     *
     * 三種結果分開處理，不要混成一個「失敗」：
     *   有文字 → PARSED，接著跑字典
     *   沒文字 → NO_TEXT_LAYER，明確告訴使用者這是掃描檔
     *   拋例外 → FAILED，訊息只進資料庫不回前端
     */
    private void parseAndExtract(ResumeFile file, byte[] content) {
        TextExtractor.Result result = textExtractor.extract(content);

        if (result.isFailure()) {
            file.markFailed(result.error());
            return;
        }
        if (!result.hasTextLayer()) {
            file.markNoTextLayer();
            return;
        }

        file.markParsed(result.text());

        // 已經被拒絕過的候選不要復活。
        // 沒有這個判斷的話，使用者重新解析同一份檔案就要再拒絕一次同樣的東西。
        Set<String> alreadyRejected = candidateRepository
                .findByResumeFileIdAndStatus(file.getId(), ExtractionCandidate.Status.REJECTED)
                .stream()
                .map(c -> c.getKind() + "|" + c.getNormalized())
                .collect(java.util.stream.Collectors.toSet());

        for (DictionaryExtractor.Match match : dictionaryExtractor.extract(result.text())) {
            if (alreadyRejected.contains(match.kind() + "|" + match.canonical())) continue;

            candidateRepository
                    .findByResumeFileIdAndKindAndNormalizedAndExtractor(
                            file.getId(), match.kind(), match.canonical(), DictionaryExtractor.NAME)
                    .orElseGet(() -> candidateRepository.save(new ExtractionCandidate(
                            file.getId(), file.getUserId(), match.kind(),
                            match.matched(), match.canonical(),
                            match.confidence(), DictionaryExtractor.NAME)));
        }
    }

    public List<ResumeFile> list() {
        return fileRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id());
    }

    public ResumeFile get(UUID id) {
        return fileRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("ResumeFile", id));
    }

    public List<ExtractionCandidate> candidates(UUID fileId) {
        get(fileId);   // 所有權檢查
        return candidateRepository.findByResumeFileIdOrderByConfidenceDesc(fileId);
    }

    /**
     * 接受候選 → 產生一個積木。
     *
     * 積木的內容留空給使用者自己填——解析只能告訴你「你會 Kubernetes」，
     * 沒辦法幫你寫出「我用 Kubernetes 做了什麼」。那句話只有你寫得出來，
     * 而那句話才是履歷上真正有價值的東西。
     */
    @Transactional
    public ExtractionCandidate accept(UUID candidateId) {
        ExtractionCandidate candidate = candidateRepository
                .findByIdAndUserId(candidateId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("ExtractionCandidate", candidateId));

        if (candidate.getStatus() == ExtractionCandidate.Status.ACCEPTED) {
            return candidate;
        }

        BlockType type = candidate.getKind() == ExtractionCandidate.Kind.CERTIFICATION
                ? BlockType.CERTIFICATION
                : BlockType.SKILL;

        Block block = blockRepository.save(new Block(
                currentUser.id(), type, candidate.getNormalized(), candidate.getNormalized()));

        candidate.accept(block.getId());
        return candidate;
    }

    @Transactional
    public ExtractionCandidate reject(UUID candidateId) {
        ExtractionCandidate candidate = candidateRepository
                .findByIdAndUserId(candidateId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("ExtractionCandidate", candidateId));

        candidate.reject();
        return candidate;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 保證存在的演算法，走到這裡代表 JVM 壞了
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @SuppressWarnings("unused")
    private static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
