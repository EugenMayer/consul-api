package com.ecwid.consul.v1;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.ecwid.consul.ConsulException;
import com.ecwid.consul.transport.ConsulHttpResponse;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Vasily Vasilkov (vgv@ecwid.com)
 * @author Jon Huang (jon5477)
 *
 */
public final class OperationException extends ConsulException {
	private static final long serialVersionUID = 7189026270432209391L;
	private final int statusCode;
	private final transient JsonNode statusContent;

	public OperationException(int statusCode, @Nullable JsonNode statusContent) {
		super(message(statusCode, statusContent));
		this.statusCode = statusCode;
		this.statusContent = statusContent;
	}

	private static String message(int statusCode, @Nullable JsonNode statusContent) {
		String message = "Consul operation exception; HTTP Status: " + statusCode;
		if (statusContent == null) {
			return message;
		}
		// text error bodies as plain text, JSON bodies as JSON
		return message + "; " + (statusContent.isTextual() ? statusContent.asText() : statusContent.toString());
	}

	public OperationException(@NonNull ConsulHttpResponse httpResponse) {
		this(httpResponse.getStatusCode(), httpResponse.getContent());
	}

	public final int getStatusCode() {
		return statusCode;
	}

	@Nullable
	public final JsonNode getStatusContent() {
		return statusContent;
	}

	@Override
	public final String toString() {
		return "OperationException [statusCode=" + statusCode + ", statusContent=" + statusContent + "]";
	}
}