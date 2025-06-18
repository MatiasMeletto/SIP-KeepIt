package com.uade.tpo.keepit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("com.uade.tpo.keepit.repository")
@EntityScan("com.uade.tpo.keepit.entities")
public class KeepitApplication {
    public static void main(String[] args) {
        SpringApplication.run(KeepitApplication.class, args);
    }
}
