package com.ecwid.consul.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.ecwid.consul.v1.Request;

/**
 * Checks the URI that is sent for a {@link Request}.
 */
class RequestUriTest {
	@SuppressWarnings("unchecked")
	private static URI sentUri(Request request) throws Exception {
		HttpClient httpClient = Mockito.mock(HttpClient.class);
		HttpResponse<InputStream> resp = Mockito.mock(HttpResponse.class);
		Mockito.when(resp.statusCode()).thenReturn(200);
		Mockito.when(resp.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
		Mockito.when(resp.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
		Mockito.when(httpClient.send(Mockito.any(HttpRequest.class), Mockito.any(BodyHandler.class))).thenReturn(resp);
		new DefaultHttpTransport(httpClient).makeRequest(URI.create("http://localhost:8500"), HttpMethod.GET, request);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		Mockito.verify(httpClient).send(captor.capture(), Mockito.any(BodyHandler.class));
		return captor.getValue().uri();
	}

	/** Decodes a raw query the way Consul (Go's url.ParseQuery) does. */
	private static Map<String, String> decodeQuery(String rawQuery) {
		Map<String, String> params = new LinkedHashMap<>();
		Arrays.stream(rawQuery.split("&")).forEach(pair -> {
			int eq = pair.indexOf('=');
			String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			params.put(key, value);
		});
		return params;
	}

	@Test
	void reservedCharactersInQueryValuesAreEncoded() throws Exception {
		String filter = "\"c++&x=y\" in Service.Tags";
		URI uri = sentUri(new Request.Builder("/v1/health/service/web").addQueryParameter("filter", filter)
				.addQueryParameter("dc", "dc 1").build());
		assertEquals(Map.of("filter", filter, "dc", "dc 1"), decodeQuery(uri.getRawQuery()));
	}

	@Test
	void flagParametersHaveNoValue() throws Exception {
		URI uri = sentUri(new Request.Builder("/v1/kv/prefix").addQueryParameter("recurse", null).build());
		assertEquals("recurse", uri.getRawQuery());
	}

	@Test
	void noQueryParameters() throws Exception {
		URI uri = sentUri(new Request.Builder("/v1/kv/key").build());
		assertEquals("http://localhost:8500/v1/kv/key", uri.toString());
	}

	@Test
	void reservedCharactersInPathAreEncoded() throws Exception {
		URI uri = sentUri(new Request.Builder("/v1/kv/a b?c#d%e").build());
		assertEquals("/v1/kv/a%20b%3Fc%23d%25e", uri.getRawPath());
		assertEquals(null, uri.getRawQuery());
	}

}
