package com.soanar.service;

import com.soanar.model.User;
import com.soanar.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public List<User> listAll() {
		return userRepository.findAll();
	}

	public Optional<User> findByEmail(String email) {
		return userRepository.findBySchoolEmail(email);
	}

	public User createOrUpdate(String email, String role, String name) {
		return createOrUpdate(email, role, name, null);
	}

	public User createOrUpdate(String email, String role, String name, String photoUrl) {
		Optional<User> found = userRepository.findBySchoolEmail(email);
		if (found.isPresent()) {
			User u = found.get();
			if (name != null && !name.isBlank()) {
				u.setName(name);
			}
			u.setRole(role);
			if (photoUrl != null && !photoUrl.isBlank()) {
				u.setPhotoUrl(photoUrl);
			}
			return userRepository.save(u);
		}
		User u = new User(email, role, name);
		u.setIsActive(true);
		if (photoUrl != null && !photoUrl.isBlank()) {
			u.setPhotoUrl(photoUrl);
		}
		return userRepository.save(u);
	}
}
