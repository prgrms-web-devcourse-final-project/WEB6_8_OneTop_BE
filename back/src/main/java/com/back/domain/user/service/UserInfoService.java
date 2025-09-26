package com.back.domain.user.service;

import com.back.domain.user.dto.UserInfoResponse;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserInfoService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return toResponse(user);
    }
}
