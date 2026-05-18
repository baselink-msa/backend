package com.baseball.auth.service;

import com.baseball.auth.common.BusinessException;
import com.baseball.auth.domain.User;
import com.baseball.auth.domain.UserRole;
import com.baseball.auth.domain.UserStatus;
import com.baseball.auth.dto.LoginRequest;
import com.baseball.auth.dto.LoginResponse;
import com.baseball.auth.dto.SignupRequest;
import com.baseball.auth.dto.UserResponse;
import com.baseball.auth.repository.UserRepository;
import com.baseball.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("DUPLICATE_EMAIL", HttpStatus.CONFLICT,
                    "이미 가입된 이메일입니다.");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS",
                        HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                    "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        if (!user.isActive()) {
            throw new BusinessException("INACTIVE_USER", HttpStatus.FORBIDDEN,
                    "비활성화된 계정입니다.");
        }
        String token = jwtTokenProvider.createToken(user.getUserId(), user.getRole().name());
        return new LoginResponse(token, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }
}
