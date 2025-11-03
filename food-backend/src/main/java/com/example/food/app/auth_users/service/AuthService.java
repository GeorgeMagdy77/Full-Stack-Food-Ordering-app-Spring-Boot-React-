package com.example.food.app.auth_users.service;

import com.example.food.app.auth_users.dtos.LoginRequest;
import com.example.food.app.auth_users.dtos.LoginResponse;
import com.example.food.app.auth_users.dtos.RegistrationRequest;
import com.example.food.app.response.Response;

public interface AuthService {
    Response<?> register(RegistrationRequest registrationRequest);
    Response<LoginResponse> login(LoginRequest loginRequest);
}
