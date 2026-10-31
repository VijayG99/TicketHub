package com.tickethub.gateway.controller;

import java.util.List;
import lombok.Data;

@Data
public class TokenRequest {
    private String userId;
    private String email;
    private List<String> roles;
    private String adminKey;
}
