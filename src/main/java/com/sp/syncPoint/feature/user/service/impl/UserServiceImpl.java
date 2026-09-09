package com.sp.syncPoint.feature.user.service.impl;

import com.sp.syncPoint.client.dto.request.RegisterRequest;
import com.sp.syncPoint.client.dto.response.InternalAuthApiResponse;
import com.sp.syncPoint.client.dto.response.RegisterResponse;
import com.sp.syncPoint.client.service.InternalAuthClient;
import com.sp.syncPoint.common.util.UniqueValueGeneratorService;
import com.sp.syncPoint.feature.auth.service.impl.RegistrationValidator;

import com.sp.syncPoint.feature.auth.dto.RegistrationRequest;
import com.sp.syncPoint.feature.user.enums.UserStatus;
import com.sp.syncPoint.common.exception.InternalAuthRegistrationFailedException;
import com.sp.syncPoint.common.exception.RegistrationException;
import com.sp.syncPoint.common.exception.UserAlreadyExistsException;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import com.sp.syncPoint.feature.auth.dto.UserRegistrationResponse;
import com.sp.syncPoint.feature.user.service.UserService;
import com.sp.syncPoint.feature.wallet.service.WalletProvisioningService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static com.sp.syncPoint.common.constants.GeneralConstants.DATE_FORMAT;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final RegistrationValidator registrationValidator;

    private final WalletProvisioningService walletService;

    private final UniqueValueGeneratorService uniqueValueGeneratorService;

    private final InternalAuthClient authClient;

    @Override
    @Transactional
    public UserRegistrationResponse registerUser(RegistrationRequest request) {

        registrationValidator.validateRegistrationRequest(request);

        Optional<User> existingUser = userRepository
                .findByPhoneNumberOrEmailOrUsername(
                        request.getPhoneNumber(),
                        request.getEmail(),
                        request.getUsername()
                );

        if (existingUser.isPresent()) {
            User found = existingUser.get();

            if (found.getPhoneNumber().equals(request.getPhoneNumber())) {
                throw new UserAlreadyExistsException("A user with this phone number is already registered");
            }
            if (request.getEmail() != null && found.getEmail().equals(request.getEmail())) {
                throw new UserAlreadyExistsException("A user with this email is already registered");
            }
            if (request.getUsername() != null && found.getUsername().equals(request.getUsername())) {
                throw new UserAlreadyExistsException("This username is already taken");
            }
        }

        RegisterRequest registerRequest = new RegisterRequest(request.getUsername(), request.getPhoneNumber(), request.getEmail(), request.getPassword(), request.getConfirmPassword());

        InternalAuthApiResponse<RegisterResponse> registerResponse = authClient.register(registerRequest);

        RegisterResponse response = registerResponse.getData();

        try {
            User user = createUser(request, response.getInternalAuthUserId());
            walletService.setupWallet(user);
            return new UserRegistrationResponse(user.getUsername(), HttpServletResponse.SC_CREATED, "User registered successfully. Check your Mailbox or SMS for the verification code.");
        } catch (InternalAuthRegistrationFailedException ex) {
            authClient.deleteUser(response.getInternalAuthUserId());
            throw new RegistrationException("Registration failed, all changes have been rolled back: " + ex.getMessage());
        }
    }

    private User createUser(RegistrationRequest request, String internalAuthUserId) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        LocalDate dateOfBirth = LocalDate.parse(request.getDateOfBirth(), formatter);

        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();

        if (age < 18) {
            throw new RegistrationException("User must be at least 18 years old to register.");
        }

        User user = new User();
        user.setInternalAuthId(internalAuthUserId);
        user.setUserRefId(uniqueValueGeneratorService.generateUniqueReferenceId());
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setGender(request.getGender().name());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setAge(age);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
}