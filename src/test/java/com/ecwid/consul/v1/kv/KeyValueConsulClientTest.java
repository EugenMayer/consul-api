package com.ecwid.consul.v1.kv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;

import com.ecwid.consul.ConsulTestContainers;
import com.ecwid.consul.v1.ConsulRawClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.ecwid.consul.v1.kv.model.PutParams;

class KeyValueConsulClientTest {
	private static final Random rnd = new Random();
	// one Consul per class; every test works below its own key prefix
	private static ConsulContainer consul;
	private static KeyValueConsulClient consulClient;
	private String prefix;

	@BeforeAll
	static void startConsul() {
		consul = ConsulTestContainers.newConsulContainer();
		consul.start();
		consulClient = new KeyValueConsulClient(
				new ConsulRawClient.Builder(consul.getHost(), consul.getMappedPort(8500)).build());
	}

	@AfterAll
	static void stopConsul() {
		consul.stop();
	}

	@BeforeEach
	void createPrefix() {
		prefix = "test-" + UUID.randomUUID();
	}

	private String key(String name) {
		return prefix + "/" + name;
	}

	@Test
	void testSetKVValue() {
		String testKey = key("test_kv");
		String testValue = "test_value";
		// Make sure there is no such key before test running
		assertNull(consulClient.getKVValue(testKey).getValue());
		// Set the key
		assertTrue(consulClient.setKVValue(testKey, testValue).getValue());
		// Make sure test key exists
		assertEquals(testValue, consulClient.getKVValue(testKey).getValue().getDecodedValue());
	}

	@Test
	void testSetKVBinaryValue() {
		String testKey = key("test_binary");
		byte[] testValue = new byte[100];
		rnd.nextBytes(testValue);
		// Make sure there is no such key before test running
		assertNull(consulClient.getKVValue(testKey).getValue());
		// Set the key
		consulClient.setKVBinaryValue(testKey, testValue);
		// Make sure test key exists
		assertArrayEquals(testValue, consulClient.getKVBinaryValue(testKey).getValue().getValue());
	}

	@Test
	void testDeleteKvValue() {
		String testKey = key("test_key");
		String testValue = "test_value";
		// Make sure there is no such key before test running
		assertNull(consulClient.getKVValue(testKey).getValue());
		// Set the key
		consulClient.setKVValue(testKey, testValue);
		// Make sure test key exists
		assertEquals(testValue, consulClient.getKVValue(testKey).getValue().getDecodedValue());
		// Delete key
		assertTrue(consulClient.deleteKVValue(testKey).getValue());
		// Make sure there is no such key before test running
		assertNull(consulClient.getKVValue(testKey).getValue());
	}

	@Test
	void testDeleteKvValues() {
		String testKeyPrefix = key("test_key");
		String testValue = "test_value";
		// Prepare test keys under testKeyPrefix prefix
		for (int i = 0; i < 10; i++) {
			String testKey = testKeyPrefix + "/" + i;
			// Make sure there is no such key before test running
			assertNull(consulClient.getKVValue(testKey).getValue());
			// Set the key
			consulClient.setKVValue(testKey, testValue);
			// Make sure test key exists
			assertEquals(testValue, consulClient.getKVValue(testKey).getValue().getDecodedValue());
		}
		// Delete all keys in single shot
		assertTrue(consulClient.deleteKVValues(testKeyPrefix).getValue());
		// Make sure all keys have been deleted
		assertNull(consulClient.getKVKeysOnly(testKeyPrefix).getValue());
	}

	@Test
	void testGetMissingKeyReturnsNull() {
		Response<GetValue> response = consulClient.getKVValue(key("missing"));
		assertNull(response.getValue());
		assertNotNull(response.getConsulIndex());
	}

	@Test
	void testGetKVValuesRecursive() {
		consulClient.setKVValue(key("a"), "1");
		consulClient.setKVValue(key("sub/b"), "2");
		consulClient.setKVValue(key("sub/deeper/c"), "3");
		List<GetValue> values = consulClient.getKVValues(prefix + "/").getValue();
		assertEquals(List.of(key("a"), key("sub/b"), key("sub/deeper/c")),
				values.stream().map(GetValue::getKey).sorted().collect(Collectors.toList()));
		assertEquals(List.of("1", "2", "3"),
				values.stream().map(GetValue::getDecodedValue).sorted().collect(Collectors.toList()));
	}

	@Test
	void testGetKVValuesMissingPrefixReturnsNull() {
		assertNull(consulClient.getKVValues(key("nothing-here/")).getValue());
	}

	@Test
	void testGetKVKeysOnly() {
		consulClient.setKVValue(key("a"), "1");
		consulClient.setKVValue(key("sub/b"), "2");
		consulClient.setKVValue(key("sub/deeper/c"), "3");
		// all keys below the prefix
		assertEquals(List.of(key("a"), key("sub/b"), key("sub/deeper/c")),
				consulClient.getKVKeysOnly(prefix + "/").getValue().stream().sorted().collect(Collectors.toList()));
		// with a separator only the first level is listed, "folders" end with the separator
		assertEquals(List.of(key("a"), key("sub/")), consulClient.getKVKeysOnly(prefix + "/", "/").getValue().stream()
				.sorted().collect(Collectors.toList()));
	}

	@Test
	void testCheckAndSet() {
		String testKey = key("cas");
		PutParams createOnly = new PutParams();
		createOnly.setCas(0L);
		// cas=0 only writes if the key does not exist yet
		assertTrue(consulClient.setKVValue(testKey, "first", createOnly).getValue());
		assertFalse(consulClient.setKVValue(testKey, "second", createOnly).getValue());
		assertEquals("first", consulClient.getKVValue(testKey).getValue().getDecodedValue());

		long modifyIndex = consulClient.getKVValue(testKey).getValue().getModifyIndex();
		PutParams current = new PutParams();
		current.setCas(modifyIndex);
		assertTrue(consulClient.setKVValue(testKey, "third", current).getValue());
		// the same index is stale now
		assertFalse(consulClient.setKVValue(testKey, "fourth", current).getValue());
		assertEquals("third", consulClient.getKVValue(testKey).getValue().getDecodedValue());
	}

	@Test
	void testFlags() {
		String testKey = key("flags");
		PutParams putParams = new PutParams();
		putParams.setFlags(42L);
		assertTrue(consulClient.setKVValue(testKey, "value", putParams).getValue());
		assertEquals(42L, consulClient.getKVValue(testKey).getValue().getFlags());
	}

	@Test
	void testKeysWithSpecialCharacters() {
		// Consul decodes the key path twice, so a key containing an escape-like sequence such as "%20"
		// cannot be stored through the HTTP API by any client; a plain "%" works
		for (String name : List.of("with space", "umlaut-äöü", "日本語", "plus+sign", "a.b-c_d~e", "question?mark",
				"hash#tag", "percent%sign", "100%", "amp&eq=sign")) {
			String testKey = key(name);
			assertTrue(consulClient.setKVValue(testKey, name).getValue(), name);
			GetValue value = consulClient.getKVValue(testKey).getValue();
			assertNotNull(value, name);
			assertEquals(testKey, value.getKey(), name);
			assertEquals(name, value.getDecodedValue(), name);
		}
	}

	@Test
	void testValueWithUnicode() {
		String testKey = key("unicode");
		String testValue = "äöü ß € 日本語 😀";
		consulClient.setKVValue(testKey, testValue);
		assertEquals(testValue, consulClient.getKVValue(testKey).getValue().getDecodedValue());
	}

	@Test
	void testBlockingQueryReturnsOnChange() throws Exception {
		String testKey = key("blocking");
		consulClient.setKVValue(testKey, "before");
		long index = consulClient.getKVValue(testKey).getConsulIndex();

		CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			consulClient.setKVValue(testKey, "after");
		});
		long start = System.nanoTime();
		QueryParams blocking = new QueryParams.Builder().setIndex(index).setWaitTime(Duration.ofSeconds(30)).build();
		Response<GetValue> response = consulClient.getKVValue(testKey, blocking);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
		writer.get(5, TimeUnit.SECONDS);

		assertEquals("after", response.getValue().getDecodedValue());
		assertTrue(response.getConsulIndex() > index);
		assertTrue(elapsed.compareTo(Duration.ofSeconds(20)) < 0, "returned only after " + elapsed);
	}

	@Test
	void testBlockingQueryTimesOutWithoutChange() {
		String testKey = key("blocking-timeout");
		consulClient.setKVValue(testKey, "unchanged");
		long index = consulClient.getKVValue(testKey).getConsulIndex();

		long start = System.nanoTime();
		QueryParams blocking = new QueryParams.Builder().setIndex(index).setWaitTime(Duration.ofSeconds(2)).build();
		Response<GetValue> response = consulClient.getKVValue(testKey, blocking);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		assertEquals("unchanged", response.getValue().getDecodedValue());
		assertEquals(index, response.getConsulIndex());
		// Consul adds up to wait/16 of jitter
		assertTrue(elapsed.compareTo(Duration.ofMillis(1500)) >= 0, "returned already after " + elapsed);
	}
}
