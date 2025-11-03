package com.example.food.app.role.service;

import com.example.food.app.response.Response;
import com.example.food.app.role.dto.RoleDTO;
import java.util.List;

public interface RoleService {


    Response<RoleDTO> createRole(RoleDTO roleDTO);

    Response<RoleDTO> updateRole(RoleDTO roleDTO);

    Response<List<RoleDTO>> getAllRoles();

    Response<?> deleteRole(Long id);
}