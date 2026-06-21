package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.RegistrationRequest;
import com.sp.sync_point.exception.KeycloakRegistrationFailedException;
import com.sp.sync_point.exception.RegistrationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KeycloakService {

    @Value("${keycloak.realm}")
    private String realm;

    private final Keycloak keycloak;

    public String registerUser(RegistrationRequest request) {

        UserRepresentation user = getUserRepresentation(request);

        Response response = keycloak.realm(realm).users().create(user);

        String keycloakId;

        if (response.getStatus() == 201) {
            String location = response.getHeaderString("Location");
            keycloakId = location.substring(location.lastIndexOf("/") + 1);
        } else if (response.getStatus() == 409) {
            throw new RegistrationException("Username or email already exists in Keycloak");
        } else {
            throw new RegistrationException("Failed to register user in Keycloak: " + response.getStatus());
        }

        return keycloakId;
    }

    public void deleteUser(String keycloakId) {
        try {
            keycloak.realm(realm).users().get(keycloakId).remove();
        } catch(KeycloakRegistrationFailedException ex) {
            throw new RegistrationException("Failed to rollback Keycloak user: " + ex.getMessage());
        }
    }

    private static UserRepresentation getUserRepresentation(RegistrationRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setEnabled(true);
        user.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getPassword());
        credential.setTemporary(false);

        user.setCredentials(List.of(credential));
        return user;
    }
}
