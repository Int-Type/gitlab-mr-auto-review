package com.inttype.codereview.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
	private String role;    // "system" | "user" | "assistant"
	private String content; // 본문

	public ChatMessage() {
	}

	public ChatMessage(String role, String content) {
		this.role = role;
		this.content = content;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
