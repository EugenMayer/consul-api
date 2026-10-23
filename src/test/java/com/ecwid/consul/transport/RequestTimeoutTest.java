package com.ecwid.consul.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Request;

/**
 * Checks the HTTP timeout used for normal and blocking requests.
 */
class RequestTimeoutTest {
	@SuppressWarnings("unchecked")
	private static Duration sentTimeout(Request request) throws Exception {
		HttpClient httpClient = Mockito.mock(HttpClient.class);
		HttpResponse<InputStream> resp = Mockito.mock(HttpResponse.class);
		Mockito.when(resp.statusCode()).thenReturn(200);
		Mockito.when(resp.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
		Mockito.when(resp.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
		Mockito.when(httpClient.send(Mockito.any(HttpRequest.class), Mockito.any(BodyHandler.class))).thenReturn(resp);
		new DefaultHttpTransport(httpClient).makeRequest(URI.create("http://localhost:8500"), HttpMethod.GET, request);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		Mockito.verify(httpClient).send(captor.capture(), Mockito.any(BodyHandler.class));
		return captor.getValue().timeout().orElseThrow();
	}

	@Test
	void normalRequestUsesDefaultTimeout() throws Exception {
		assertEquals(ClientUtils.DEFAULT_REQUEST_TIMEOUT, sentTimeout(new Request.Builder("/v1/kv/key").build()));
	}

	@Test
	void blockingRequestWaitsForWaitTimePlusJitterAndMargin() throws Exception {
		QueryParams blocking = new QueryParams.Builder().setIndex(5).setWaitTime(Duration.ofSeconds(32)).build();
		Duration timeout = sentTimeout(new Request.Builder("/v1/kv/key").addQueryParameters(blocking).build());
		// 32s wait + 2s (wait/16 jitter) + default margin
		assertEquals(Duration.ofSeconds(34).plus(ClientUtils.DEFAULT_REQUEST_TIMEOUT), timeout);
	}

	@Test
	void blockingRequestWithoutWaitUsesConsulDefaultWait() throws Exception {
		QueryParams blocking = new QueryParams.Builder().setIndex(5).build();
		Duration timeout = sentTimeout(new Request.Builder("/v1/kv/key").addQueryParameters(blocking).build());
		// Consul blocks up to 5 minutes when no wait is given
		Duration fiveMinutes = Duration.ofMinutes(5);
		assertEquals(fiveMinutes.plus(fiveMinutes.dividedBy(16)).plus(ClientUtils.DEFAULT_REQUEST_TIMEOUT), timeout);
	}
}
