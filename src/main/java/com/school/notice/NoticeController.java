package com.school.notice;

import com.school.common.PageResponse;
import com.school.notice.NoticeDtos.NoticeRequest;
import com.school.notice.NoticeDtos.NoticeResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<PageResponse<NoticeResponse>> list(@PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(noticeService.list(pageable));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<NoticeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(noticeService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<NoticeResponse> create(@Valid @RequestBody NoticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noticeService.create(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<NoticeResponse> update(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        return ResponseEntity.ok(noticeService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
