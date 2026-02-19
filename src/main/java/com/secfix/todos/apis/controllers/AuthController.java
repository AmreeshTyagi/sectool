package com.secfix.todos.apis.controllers;

import com.secfix.todos.database.models.Tenant;
import com.secfix.todos.database.models.UserInfo;
import com.secfix.todos.database.repositories.TenantRepository;
import com.secfix.todos.database.repositories.UserInfoRepository;
import com.secfix.todos.security.JwtService;
import com.secfix.todos.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final TenantRepository tenantRepository;
    private final UserInfoRepository userInfoRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(TenantRepository tenantRepository, UserInfoRepository userInfoRepository,
                          JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userInfoRepository = userInfoRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String tenantSlug = body.get("tenantSlug");
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");

        if (tenantSlug == null || name == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields required"));
        }

        Tenant tenant = tenantRepository.findBySlug(tenantSlug).orElseGet(() -> {
            Tenant t = new Tenant();
            t.setName(tenantSlug);
            t.setSlug(tenantSlug);
            return tenantRepository.save(t);
        });

        if (userInfoRepository.findByEmailAndTenantId(email, tenant.getId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "User already exists"));
        }

        UserInfo user = new UserInfo();
        user.setTenantId(tenant.getId());
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setIsActive(true);
        user = userInfoRepository.save(user);

        String token = jwtService.generateToken(tenant.getId(), user.getId(), email, user.getRole());
        return ResponseEntity.ok(Map.of("token", token, "tenantId", tenant.getId(), "userId", user.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String tenantSlug = body.get("tenantSlug");

        if (email == null || password == null || tenantSlug == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email, password and tenantSlug required"));
        }

        Tenant tenant = tenantRepository.findBySlug(tenantSlug).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        UserInfo user = userInfoRepository.findByEmailAndTenantId(email, tenant.getId()).orElse(null);
        if (user == null || user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        String token = jwtService.generateToken(tenant.getId(), user.getId(), email, user.getRole());
        return ResponseEntity.ok(Map.of("token", token, "tenantId", tenant.getId(), "userId", user.getId()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        UUID tenantId = TenantContext.getTenantId();
        Integer userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        UserInfo user = userInfoRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        return ResponseEntity.ok(Map.of(
            "userId", user.getId(),
            "tenantId", user.getTenantId(),
            "name", user.getName(),
            "email", user.getEmail(),
            "role", user.getRole()
        ));
    }
}
