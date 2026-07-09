package com.staffly.backend.user.dto;

public record CreateUserResponse(UserResponse user, String temporaryPassword) {
}
