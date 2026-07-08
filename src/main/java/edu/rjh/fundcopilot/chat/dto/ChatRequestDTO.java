package edu.rjh.fundcopilot.chat.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequestDTO(
        String conversationId,
        String userId,
        @NotBlank(message = "message不能为空")
        String message,
        List<String> fundCodes,
        Boolean portfolioEnabled
) {
}
