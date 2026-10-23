package com.ecwid.consul.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RequestTest {
	@Test
	void builderMethodsCanBeChained() {
		Request request = new Request.Builder("/v1/kv/key").addHeader("X-First", "1").addHeader("X-Second", "2")
				.addQueryParameter("recurse", null).build();
		assertEquals(Map.of("X-First", "1", "X-Second", "2"), request.getHeaders());
		assertEquals("/v1/kv/key", request.getEndpoint());
	}
}
