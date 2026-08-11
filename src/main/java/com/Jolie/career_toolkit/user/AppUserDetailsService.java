package com.Jolie.career_toolkit.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .map(AppUserDetails::from)
                // 訊息刻意不寫「這個 email 不存在」。
                // 一旦區分「帳號不存在」和「密碼錯誤」，任何人都可以拿這個端點
                // 逐一測試哪些 email 有註冊過——這叫使用者列舉（user enumeration）。
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
