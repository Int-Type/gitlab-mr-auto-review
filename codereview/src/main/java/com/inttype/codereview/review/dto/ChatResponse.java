package com.inttype.codereview.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {
	private List<Choice> choices;

	public ChatResponse() {}

	public ChatResponse(List<Choice> choices) {
		this.choices = choices;
	}

	public List<Choice> getChoices() { return choices; }
	public void setChoices(List<Choice> choices) { this.choices = choices; }
}
