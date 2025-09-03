package com.inttype.codereview.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {
	private String model;
	private List<ChatMessage> messages;

	@JsonProperty("temperature")
	private Double temperature;

	public ChatRequest() {}

	public ChatRequest(String model, List<ChatMessage> messages, Double temperature) {
		this.model = model;
		this.messages = messages;
		this.temperature = temperature;
	}

	public String getModel() { return model; }
	public void setModel(String model) { this.model = model; }

	public List<ChatMessage> getMessages() { return messages; }
	public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

	public Double getTemperature() { return temperature; }
	public void setTemperature(Double temperature) { this.temperature = temperature; }
}
