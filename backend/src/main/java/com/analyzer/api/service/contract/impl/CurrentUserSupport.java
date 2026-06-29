package com.analyzer.api.service.contract.impl;

import com.analyzer.api.security.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class CurrentUserSupport {

    private CurrentUserSupport() {
    }

    static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thong tin xac thuc khong hop le");
    }
}
