package com.school.exam;

import com.school.exam.ExamDtos.MyExamResult;
import com.school.exam.ExamDtos.StudentResultRow;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
public class ResultSelfController {

    private final ResultService resultService;

    public ResultSelfController(ResultService resultService) {
        this.resultService = resultService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/results")
    public ResponseEntity<List<MyExamResult>> myResults() {
        return ResponseEntity.ok(resultService.myResults());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/results/{examId}")
    public ResponseEntity<StudentResultRow> myExamResult(@PathVariable Long examId) {
        return ResponseEntity.ok(resultService.myExamResult(examId));
    }
}
