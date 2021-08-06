package com.speram.nshopper.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speram.nshopper.dao.RoleRepository;
import com.speram.nshopper.dao.UserRepository;
import com.speram.nshopper.modal.AppUser;
import com.speram.nshopper.modal.Role;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private UserRepository userRepository;
    private RoleRepository roleRepository;

    public AppUser saveUser(AppUser appUser) {
        appUser.setPassword(new BCryptPasswordEncoder().encode(appUser.getPassword()));
        return userRepository.save(appUser);
    }

    public Role saveRole(Role role) {
        return roleRepository.save(role);
    }

    public AppUser findUserByName(String username) {
        return userRepository.findByUsername(username);
    }

    public void addRoleToUser(String username, String roleName) {
        AppUser appUser = userRepository.findByUsername(username);
        Role role = roleRepository.findByName(roleName);
        appUser.getRoles().add(role);
    }

    public List<AppUser> findAll() {
        return userRepository.findAll();
    }


    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authrizationHeader = request.getHeader("Authorization");
        if (authrizationHeader != null && authrizationHeader.startsWith("Bearer ")) {
            try {
                String refresh_token = authrizationHeader.substring("Bearer ".length());
                Algorithm algorithm = Algorithm.HMAC256("secreat".getBytes());
                JWTVerifier verifier = JWT.require(algorithm).build();
                DecodedJWT decodedJWT = verifier.verify(refresh_token);
                String username = decodedJWT.getSubject();
                AppUser user = this.findUserByName(username);
                List authorities = user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toList());
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                String access_token = JWT.create()
                        .withSubject(user.getUsername())
                        .withExpiresAt(new Date(System.currentTimeMillis() + 10 * 60 * 1000))
                        .withIssuer(request.getRequestURI().toString())
                        .withClaim("roles", user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toList()))
                        .sign(algorithm);
                Map<String, String> tokens = new HashMap<>();
                tokens.put("access_token", access_token);
                tokens.put("refresh_token", refresh_token);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                new ObjectMapper().writeValue(response.getOutputStream(), tokens);

            } catch (Exception e) {
                log.error("Error in: {}", e.getMessage());
                response.setHeader("error", e.getMessage());
                response.setStatus(HttpStatus.FORBIDDEN.value());
                HashMap<String, String> map = new HashMap<>();
                map.put("error", "Invalid refresh token");
                new ObjectMapper().writeValue(response.getOutputStream(), map);
            }
        } else {
            throw new RuntimeException("Refresh token is missing");
        }
    }
}
