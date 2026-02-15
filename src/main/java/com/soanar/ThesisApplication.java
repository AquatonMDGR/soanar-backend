package com.soanar;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.soanar.model.User;
import com.soanar.model.Announcement;
import com.soanar.repository.UserRepository;
import com.soanar.repository.AnnouncementRepository;

import java.util.Optional;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableRetry
public class ThesisApplication {

	public static void main(String[] args) {
		SpringApplication.run(ThesisApplication.class, args);
	}

	@Bean
	public org.springframework.boot.CommandLineRunner seedData(UserRepository userRepository,
															   AnnouncementRepository announcementRepository,
															   org.springframework.core.env.Environment env) {
		return args -> {
			String seedFlag = env.getProperty("seed.db", "false");
			if (!seedFlag.equalsIgnoreCase("true")) {
				return; // Only seed when seed.db=true is set
			}

			// Create a demo user if not exists
			Optional<User> osas = userRepository.findBySchoolEmail("osas@iacademy.edu.ph");
			if (osas.isEmpty()) {
				User u = new User("osas@iacademy.edu.ph", "OSAS", "OSAS Officer");
				osas = Optional.of(userRepository.save(u));
			}

			Optional<User> org = userRepository.findBySchoolEmail("org@iacademy.edu.ph");
			if (org.isEmpty()) {
				User u = new User("org@iacademy.edu.ph", "Student Organization", "Student Org Rep");
				org = Optional.of(userRepository.save(u));
			}

			// Create a sample announcement if none exist
			if (announcementRepository.count() == 0) {
				Announcement a = new Announcement();
				a.setTitle("Welcome to iACADEMY");
				a.setDescription("Orientation week starts Monday. See details on the portal.");
				a.setPostedBy(org.get());
				a.setStatus("PENDING");
				announcementRepository.save(a);
			}
		};
	}

}
