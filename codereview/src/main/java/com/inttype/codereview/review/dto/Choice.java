package com.inttype.codereview.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Choice {
	private ChatMessage message;
	// 필요 시 index, finish_reason 등 확장 필드도 추가 가능
	// private Integer index;
	// @JsonProperty("finish_reason") private String finishReason;

	public Choice() {}

	public Choice(ChatMessage message) {
		this.message = message;
	}

	public ChatMessage getMessage() { return message; }
	public void setMessage(ChatMessage message) { this.message = message; }
}
