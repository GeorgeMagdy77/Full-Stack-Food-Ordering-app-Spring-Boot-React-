package com.example.food.app.auth_users.service;

import com.example.food.app.auth_users.dtos.UserDTO;
import com.example.food.app.auth_users.entity.User;
import com.example.food.app.response.Response;

import java.util.List;

public interface UserService {


    User getCurrentLoggedInUser();

    Response<List<UserDTO>> getAllUsers();

    Response<UserDTO> getOwnAccountDetails();

    Response<?> updateOwnAccount(UserDTO userDTO);

    Response<?> deactivateOwnAccount();

}