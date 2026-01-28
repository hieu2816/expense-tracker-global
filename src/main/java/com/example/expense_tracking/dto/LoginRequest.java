package com.example.expense_tracking.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
