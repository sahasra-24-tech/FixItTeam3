package com.fixitnow.controller;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixitnow.dto.JwtResponse;
import com.fixitnow.dto.LoginRequest;
import com.fixitnow.dto.SignupRequest;
import com.fixitnow.model.User;
import com.fixitnow.model.PasswordResetToken;
import com.fixitnow.repository.UserRepository;
import com.fixitnow.repository.PasswordResetTokenRepository;
import com.fixitnow.security.JwtUtils;
import com.fixitnow.security.UserPrincipal;
import com.fixitnow.service.EmailService;

import jakarta.validation.Valid;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;
    
    @Autowired
    EmailService emailService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // Hardcoded admin credentials (temporary fix)
            final String ADMIN_EMAIL = "admin@example.com";
            final String ADMIN_PASSWORD = "admin123";
            final String ADMIN_NAME = "Admin";
            
            // Check if this is the hardcoded admin trying to log in
            if (loginRequest.getEmail().equalsIgnoreCase(ADMIN_EMAIL)) {
                if (loginRequest.getPassword().equals(ADMIN_PASSWORD)) {
                    String jwt = jwtUtils.generateJwtToken(ADMIN_EMAIL, "ADMIN");
                    String refreshToken = jwtUtils.generateRefreshToken(ADMIN_EMAIL);

                    Map<String, Object> response = new HashMap<>();
                    response.put("accessToken", jwt);
                    response.put("refreshToken", refreshToken);
                    response.put("type", "Bearer");
                    response.put("id", 0L);
                    response.put("name", ADMIN_NAME);
                    response.put("email", ADMIN_EMAIL);
                    response.put("role", "ADMIN");
                    response.put("location", "");
                    response.put("phone", "");
                    response.put("profileImage", null);
                    response.put("avatarUrl", null);
                    
                    return ResponseEntity.ok(response);
                } else {
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Incorrect password. Please try again.");
                    return ResponseEntity.badRequest().body(error);
                }
            }
            
            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElse(null);
                    
            if (user == null) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "No account found with this email address");
                return ResponseEntity.badRequest().body(error);
            }
            

            Authentication authentication;
            try {
                authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
            } catch (Exception authException) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Incorrect password. Please try again.");
                return ResponseEntity.badRequest().body(error);
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);
                    
            if (user.getRole() == User.Role.PROVIDER && (user.getIsVerified() == null || !user.getIsVerified())) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Admin Not Approved This profile yet Please Wait We'll Get Reach You Soon");
                return ResponseEntity.status(403).body(error);
            }

            String jwt = jwtUtils.generateJwtToken(user.getEmail(), user.getRole().name());
            String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", jwt);
            response.put("refreshToken", refreshToken);
            response.put("type", "Bearer");
            response.put("id", user.getId());
            response.put("name", user.getName());
            response.put("email", user.getEmail());
            response.put("role", user.getRole().name());
            response.put("location", user.getLocation());
            response.put("phone", user.getPhone());
            response.put("profileImage", user.getProfileImage());
            response.put("avatarUrl", user.getProfileImage());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Authentication failed. Please check your credentials.");
            return ResponseEntity.badRequest().body(error);
        }
    }
    @GetMapping("/health")
public ResponseEntity<String> healthCheck() {
    return ResponseEntity.ok("Backend is alive!");
}


    // ... (rest of your signup, refresh, admin-register, me, forgot-password, reset-password methods remain unchanged)
}

