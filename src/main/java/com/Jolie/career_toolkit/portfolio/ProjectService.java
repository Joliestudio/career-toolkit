package com.Jolie.career_toolkit.portfolio;

import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.portfolio.dto.SaveProjectRequest;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CurrentUser currentUser;

    public ProjectService(ProjectRepository projectRepository, CurrentUser currentUser) {
        this.projectRepository = projectRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public Project create(SaveProjectRequest request) {
        Project project = new Project(currentUser.id(), request.name());
        apply(project, request);
        return projectRepository.save(project);
    }

    public List<Project> listMine() {
        return projectRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAsc(currentUser.id());
    }

    /**
     * 公開作品集。**不需要登入**，所以這個方法絕對不能碰 CurrentUser。
     *
     * isPublic 的過濾寫在 repository 的方法名裡而不是撈回來再篩：
     * 撈回來再篩的話，只要哪天有人忘了那行 filter，私人專案就直接公開了，
     * 而且不會有任何錯誤訊息。
     */
    public List<Project> listPublic(UUID userId) {
        return projectRepository
                .findByUserIdAndIsPublicTrueAndDeletedAtIsNullOrderBySortOrderAsc(userId);
    }

    public Project get(UUID id) {
        return projectRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    @Transactional
    public Project update(UUID id, SaveProjectRequest request) {
        Project project = get(id);
        apply(project, request);
        return project;
    }

    @Transactional
    public void delete(UUID id) {
        get(id).markAsDeleted();
    }

    private void apply(Project project, SaveProjectRequest request) {
        if (request.name() != null) project.setName(request.name());
        if (request.summary() != null) project.setSummary(request.summary());
        if (request.repoUrl() != null) project.setRepoUrl(request.repoUrl());
        if (request.demoUrl() != null) project.setDemoUrl(request.demoUrl());
        if (request.techStack() != null) project.setTechStack(request.techStack());
        if (request.role() != null) project.setRole(request.role());
        if (request.startedOn() != null) project.setStartedOn(request.startedOn());
        if (request.endedOn() != null) project.setEndedOn(request.endedOn());
        if (request.isPublic() != null) project.setIsPublic(request.isPublic());
        if (request.sortOrder() != null) project.setSortOrder(request.sortOrder());
        if (request.blockId() != null) project.setBlockId(request.blockId());
    }
}
