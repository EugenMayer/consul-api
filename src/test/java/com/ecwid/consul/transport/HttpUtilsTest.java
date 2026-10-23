package com.ecwid.consul.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HttpUtilsTest {
	/**
	 * Records whether the body stream was closed; an unclosed body keeps the
	 * HTTP connection from being reused.
	 */
	private static final class TrackingInputStream extends ByteArrayInputStream {
		private boolean closed;

		TrackingInputStream(String body) {
			super(body.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void close() {
			closed = true;
		}
	}

	@SuppressWarnings("unchecked")
	private static HttpResponse<InputStream> response(int status, String contentType, InputStream body) {
		HttpResponse<InputStream> resp = Mockito.mock(HttpResponse.class);
		Map<String, List<String>> headers = contentType == null ? Map.of() : Map.of("Content-Type", List.of(contentType));
		Mockito.when(resp.statusCode()).thenReturn(status);
		Mockito.when(resp.headers()).thenReturn(HttpHeaders.of(headers, (k, v) -> true));
		Mockito.when(resp.body()).thenReturn(body);
		return resp;
	}

	@Test
	void jsonBodyIsParsedAndClosed() throws Exception {
		TrackingInputStream body = new TrackingInputStream("{\"a\":1}");
		ConsulHttpResponse parsed = HttpUtils.parseResponse(response(200, "application/json", body));
		assertEquals(1, parsed.getContent().get("a").asInt());
		assertTrue(body.closed);
	}

	@Test
	void jsonContentTypeWithCharsetIsParsed() throws Exception {
		TrackingInputStream body = new TrackingInputStream("true");
		ConsulHttpResponse parsed = HttpUtils.parseResponse(response(200, "application/json; charset=utf-8", body));
		assertTrue(parsed.getContent().asBoolean());
	}

	@Test
	void plainTextErrorBodyIsKeptAndClosed() throws Exception {
		TrackingInputStream body = new TrackingInputStream("rpc error making call: EOF\n");
		ConsulHttpResponse parsed = HttpUtils.parseResponse(response(500, "text/plain; charset=utf-8", body));
		assertEquals(500, parsed.getStatusCode());
		assertEquals("rpc error making call: EOF", parsed.getContent().asText());
		assertTrue(body.closed);
	}

	@Test
	void emptyNonJsonBodyHasNoContent() throws Exception {
		TrackingInputStream body = new TrackingInputStream("");
		ConsulHttpResponse parsed = HttpUtils.parseResponse(response(200, null, body));
		assertNull(parsed.getContent());
		assertTrue(body.closed);
	}
}
