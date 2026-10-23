package com.ecwid.consul.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;

import com.ecwid.consul.ConsulTestContainers;
import com.ecwid.consul.TimedOutException;
import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.kv.model.PutParams;

/**
 * How failures surface to callers: HTTP errors, unreachable agents and
 * interrupted blocking queries.
 */
class ErrorHandlingIntegrationTest {
	private static ConsulContainer consul;
	private static ConsulClient client;

	@BeforeAll
	static void startConsul() {
		consul = ConsulTestContainers.newConsulContainer();
		consul.start();
		client = new ConsulClient(consul.getHost(), consul.getMappedPort(8500));
	}

	@AfterAll
	static void stopConsul() {
		consul.stop();
	}

	private static String uniqueKey() {
		return "errors/" + UUID.randomUUID();
	}

	private static void assertErrorText(OperationException e, String expectedPart) {
		assertNotNull(e.getStatusContent(), "error body is missing");
		String text = e.getStatusContent().asText();
		assertTrue(text.toLowerCase(Locale.ROOT).contains(expectedPart), "unexpected error body: " + text);
		assertTrue(e.getMessage().contains(text), "message does not contain the error body: " + e.getMessage());
	}

	@Test
	void missingKeyIsNotAnError() {
		assertNull(client.getKVValue(uniqueKey()).getValue());
		assertNull(client.getKVValues(uniqueKey() + "/").getValue());
		assertNull(client.getKVKeysOnly(uniqueKey() + "/").getValue());
	}

	@Test
	void serverSideErrorOnKvWriteThrowsOperationException() {
		// acquiring a lock with a session that does not exist is rejected by Consul with a 500
		PutParams putParams = new PutParams();
		putParams.setAcquireSession(UUID.randomUUID().toString());
		OperationException e = assertThrows(OperationException.class,
				() -> client.setKVValue(uniqueKey(), "value", putParams));
		assertEquals(500, e.getStatusCode());
		// Consul answers errors with a text/plain body, which must reach the caller
		assertErrorText(e, "session");
	}

	@Test
	void unknownCheckThrowsOperationException() {
		OperationException e = assertThrows(OperationException.class,
				() -> client.agentCheckPass("does-not-exist-" + UUID.randomUUID()));
		assertTrue(e.getStatusCode() >= 400 && e.getStatusCode() < 600, "status " + e.getStatusCode());
		assertErrorText(e, "check");
	}

	@Test
	void unreachableAgentThrowsTransportException() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		ConsulClient unreachable = new ConsulClient("localhost", closedPort);
		assertThrows(TransportException.class, () -> unreachable.getKVValue(uniqueKey()));
	}

	@Test
	void interruptedBlockingQueryThrowsTimedOutException() throws Exception {
		String key = uniqueKey();
		client.setKVValue(key, "value");
		long index = client.getKVValue(key).getConsulIndex();
		QueryParams blocking = new QueryParams.Builder().setIndex(index).setWaitTime(Duration.ofSeconds(60)).build();

		AtomicBoolean interruptFlagKept = new AtomicBoolean();
		Thread[] blockingThread = new Thread[1];
		CompletableFuture<Throwable> result = new CompletableFuture<>();
		blockingThread[0] = new Thread(() -> {
			try {
				client.getKVValue(key, blocking);
				result.complete(null);
			} catch (Throwable t) {
				interruptFlagKept.set(Thread.currentThread().isInterrupted());
				result.complete(t);
			}
		});
		blockingThread[0].start();
		TimeUnit.SECONDS.sleep(1);
		blockingThread[0].interrupt();

		Throwable thrown = result.get(10, TimeUnit.SECONDS);
		assertTrue(thrown instanceof TimedOutException, "expected TimedOutException but got " + thrown);
		assertTrue(interruptFlagKept.get(), "interrupt flag must be restored");
	}
}
