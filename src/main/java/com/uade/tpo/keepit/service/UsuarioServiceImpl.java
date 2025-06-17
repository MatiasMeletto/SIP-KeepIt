package com.uade.tpo.keepit.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.repository.UserRepository;
import java.util.List;
import java.util.Optional;

@Service
public class UsuarioServiceImpl implements UsuarioService {
    
    @Autowired
    private UserRepository userRepository;

    @Override
    public List<Usuario> getUsuarios() {
        return userRepository.findAll();
    }

    @Override
    public Optional<Usuario> getUsuarioById(Long usuarioId) {
        return userRepository.findById(usuarioId);
    }

    @Override
    public Optional<Usuario> getUsuarioByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Usuario createUsuario(Usuario usuario) {
        return userRepository.save(usuario);
    }

    @Override
    public Usuario updateUsuario(Usuario usuario) {
        if (userRepository.existsById(usuario.getId())) {
            return userRepository.save(usuario);
        }
        throw new RuntimeException("Usuario no encontrado");
    }

    @Override
    public void deleteUsuario(Long usuarioId) {
        userRepository.deleteById(usuarioId);
    }

    @Override
    public Optional<Usuario> findByEmail(String name) {
        return userRepository.findByEmail(name);
    }
}