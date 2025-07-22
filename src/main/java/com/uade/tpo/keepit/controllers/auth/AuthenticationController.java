package com.uade.tpo.keepit.controllers.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.service.ArchivoService;
import com.uade.tpo.keepit.service.AuthenticationService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;
    private final ArchivoService archivoService;

    @GetMapping("/user")
    public ResponseEntity<userDto> obtenerUsuarioActual(Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            
            // Obtener cantidad de archivos del usuario
            Long cantidadArchivos = archivoService.contarArchivosPorUsuario(usuario);
            
            userDto userResponse = userDto.builder()
                    .id(usuario.getId())
                    .email(usuario.getEmail())
                    .telefono(usuario.getTelefono())
                    .role(usuario.getRole())
                    .cantidadArchivos(cantidadArchivos)
                    .build();
            
            return ResponseEntity.ok(userResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(service.authenticate(request));
    }
}