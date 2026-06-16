package com.fixitnow.controller;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

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
            final String ADMIN_EMAIL = "admin@example.com";
            final String ADMIN_PASSWORD = "admin123";
            final String ADMIN_NAME = "Admin";
            
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

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        Map<String, String> response = new HashMap<>();
        
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            response.put("message", "Error: Email is already taken!");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            User user = new User(signUpRequest.getName(),
                               signUpRequest.getEmail(),
                               encoder.encode(signUpRequest.getPassword()),
                               User.Role.valueOf(signUpRequest.getRole().toUpperCase()));

            user.setLocation(signUpRequest.getLocation());
            user.setPhone(signUpRequest.getPhone());
            
            if ("PROVIDER".equals(signUpRequest.getRole().toUpperCase())) {
                user.setBio(signUpRequest.getBio());
                user.setExperience(signUpRequest.getExperience());
                user.setServiceArea(signUpRequest.getServiceArea());
                user.setDocumentType(signUpRequest.getDocumentType());
                user.setVerificationDocument(signUpRequest.getVerificationDocument());
                user.setIsVerified(false);
            }

            userRepository.save(user);

            response.put("message", "User registered successfully!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshtoken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        
        if (refreshToken != null && jwtUtils.validateJwtToken(refreshToken)) {
            String email = jwtUtils.getEmailFromJwtToken(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
                    
            String newAccessToken = jwtUtils.generateJwtToken(user.getEmail(), user.getRole().name());
            
            Map<String, String> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            response.put("refreshToken", refreshToken);
            
            return ResponseEntity.ok(response);
        }
        
        Map<String, String> error = new HashMap<>();
        error.put("message", "Invalid refresh token");
        return ResponseEntity.badRequest().body(error);
    }

    @PostMapping("/admin-register")
    public ResponseEntity<?> registerAdmin(@Valid @RequestBody SignupRequest signUpRequest) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Admin registration is disabled. Please contact the system administrator.");
        return ResponseEntity.status(403).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            if (userPrincipal == null) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Not authenticated");
                return ResponseEntity.status(401).body(error);
            }

            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Map<String, Object> response = new HashMap<>();
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
            error.put("message", "Error fetching user: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            
            if (email == null || email.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Email is required");
                return ResponseEntity.badRequest().body(error);
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

            String resetCode = String.format("%06d", (int)(Math.random() * 1000000));
            LocalDateTime expiryTime = LocalDateTime.now().plusHours(24);

            passwordResetTokenRepository.findByUserAndUsedFalse(user).ifPresent(
                existingToken -> passwordResetTokenRepository.delete(existingToken)
            );

            PasswordResetToken resetToken = new PasswordResetToken(resetCode, user, expiryTime);
            passwordResetTokenRepository.save(resetToken);

            try {
                emailService.sendPasswordResetEmail(email, resetCode);
                
                Map<String, String> response = new HashMap<>();
                response.put("message", "Password reset code sent to your email address.");
                return ResponseEntity.ok(response);
                
            } catch (Exception emailError) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Email service temporarily unavailable. Your reset code is: " + resetCode);
                response.put("code", resetCode);
                return ResponseEntity.ok(response);
            }
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");
            String newPassword = request.get("newPassword");

            if (email == null || email.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Email is required");
                return ResponseEntity.badRequest().body(error);
            }

            if (code == null || code.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Reset code is required");
                return ResponseEntity.badRequest().body(error);
            }

            if (newPassword == null || newPassword.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "New password is required");
                return ResponseEntity.badRequest().body(error);
            }

            if (newPassword.length() < 6) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Password must be at least 6 characters");
                return ResponseEntity.badRequest().body(error);
            }

            PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(code)
                    .orElseThrow(() -> new RuntimeException("Invalid or expired reset code"));

            if (resetToken.isExpired()) {
                passwordResetTokenRepository.delete(resetToken);
                Map<String, String> error = new HashMap<>();
                error.put("message", "Reset code has expired. Please request a new one.");
                return ResponseEntity.badRequest().body(error);
            }

            if (resetToken.getUsed()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Reset code has already been used.");
                return ResponseEntity.badRequest().body(error);
            }

            if (!resetToken.getUser().getEmail().equals(email)) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Email does not match reset code");
                return ResponseEntity.badRequest().body(error);
            }

            User user = resetToken.getUser();
            user.setPassword(encoder.encode(newPassword));
            userRepository.save(user);

            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset successfully!");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
