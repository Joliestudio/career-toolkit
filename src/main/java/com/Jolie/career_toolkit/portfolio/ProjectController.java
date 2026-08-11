package com.Jolie.career_toolkit.portfolio;

import com.Jolie.career_toolkit.portfolio.dto.ProjectResponse;
import com.Jolie.career_toolkit.portfolio.dto.SaveProjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody SaveProjectRequest request) {
        return ResponseEntity.status(201).body(ProjectResponse.from(projectService.create(request)));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.listMine().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return ProjectResponse.from(projectService.get(id));
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody SaveProjectRequest request) {
        return ProjectResponse.from(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
