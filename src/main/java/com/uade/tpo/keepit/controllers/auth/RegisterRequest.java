package com.uade.tpo.keepit.controllers.auth;

import com.uade.tpo.keepit.entities.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    private String password;
    private String email;
    private String telefono;
    private Role role;
}
