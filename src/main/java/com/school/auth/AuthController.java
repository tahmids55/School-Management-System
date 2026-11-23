package com.school.auth;

import com.school.auth.AuthDtos.CurrentUser;
import com.school.auth.AuthDtos.LoginRequest;
import com.school.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me() {
        return ResponseEntity.ok(authService.currentUser());
    }
}
