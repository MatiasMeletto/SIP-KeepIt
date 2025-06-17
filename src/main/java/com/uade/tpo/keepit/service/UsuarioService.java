package com.uade.tpo.keepit.service;

import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;
import java.util.List;
import java.util.Optional;

public interface UsuarioService {
    List<Usuario> getUsuarios();
    
    Optional<Usuario> getUsuarioById(Long usuarioId);
    
    Optional<Usuario> getUsuarioByEmail(String email);
    
    Usuario createUsuario(Usuario usuario);
    
    Usuario updateUsuario(Usuario usuario);
    
    void deleteUsuario(Long usuarioId);

    Optional<Usuario> findByEmail(String name);
}