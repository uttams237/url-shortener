package com.uttam.urlshortener.service;

import com.uttam.urlshortener.entity.User;
import com.uttam.urlshortener.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user with a BCrypt-hashed password.
     *
     * @return empty if username already exists, otherwise the created user
     */
    public Optional<User> register(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            return Optional.empty();
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword)); // BCrypt hash
        return Optional.of(userRepository.save(user));
    }

    /**
     * Authenticates a user by checking username and password.
     *
     * @return the user if credentials are valid, empty otherwise
     */
    public Optional<User> authenticate(String username, String rawPassword) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()));
    }
}
