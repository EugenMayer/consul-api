package com.ecwid.consul.v1.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;

import com.ecwid.consul.ConsulTestContainers;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Filter;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.agent.model.Service;
import com.ecwid.consul.v1.health.model.Check;
import com.ecwid.consul.v1.health.model.Check.CheckStatus;
import com.ecwid.consul.v1.health.model.HealthService;

/**
 * Service registration with TTL checks on the agent and how the health
 * endpoints report them.
 */
class HealthAgentIntegrationTest {
	private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(15);
	// one Consul per class; every test registers its own service
	private static ConsulContainer consul;
	private static ConsulClient client;
	private String serviceName;
	private String serviceId;
	private String checkId;

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

	@BeforeEach
	void names() {
		serviceName = "svc-" + UUID.randomUUID();
		serviceId = serviceName + "-1";
		// the agent names the check of a service registration "service:<service id>"
		checkId = "service:" + serviceId;
	}

	private void registerServiceWithTtlCheck(String... tags) {
		NewService.Check check = new NewService.Check();
		check.setTtl("60s");
		NewService service = new NewService();
		service.setId(serviceId);
		service.setName(serviceName);
		service.setPort(8080);
		service.setTags(List.of(tags));
		service.setCheck(check);
		client.agentServiceRegister(service);
	}

	private List<HealthService> healthServices(boolean onlyPassing) {
		HealthServicesRequest request = new HealthServicesRequest.Builder().setPassing(onlyPassing).build();
		return client.getHealthServices(serviceName, request).getValue();
	}

	private CheckStatus checkStatus() {
		return client.getHealthChecksForService(serviceName, HealthChecksForServiceRequest.newBuilder().build())
				.getValue().stream().filter(c -> c.getCheckId().equals(checkId)).map(Check::getStatus).findFirst()
				.orElse(null);
	}

	/**
	 * The agent syncs check updates to the servers asynchronously, so health
	 * results are polled.
	 */
	private static void await(String description, BooleanSupplier condition) {
		long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				fail("interrupted while waiting for: " + description);
			}
		}
		fail("timed out waiting for: " + description);
	}

	@Test
	void registeredServiceIsListedByAgent() {
		registerServiceWithTtlCheck("blue");
		Map<String, Service> services = client.getAgentServices().getValue();
		Service service = services.get(serviceId);
		assertEquals(serviceName, service.getService());
		assertEquals(8080, service.getPort());
		assertEquals(List.of("blue"), service.getTags());
	}

	@Test
	void ttlCheckStartsCriticalAndFollowsPassWarnFail() {
		registerServiceWithTtlCheck();
		// a TTL check is critical until the first update
		await("initial critical", () -> checkStatus() == CheckStatus.CRITICAL);
		assertTrue(healthServices(true).isEmpty());
		assertEquals(1, healthServices(false).size());

		client.agentCheckPass(checkId, "all good");
		await("passing", () -> checkStatus() == CheckStatus.PASSING);
		await("listed as passing", () -> healthServices(true).size() == 1);
		HealthService healthService = healthServices(true).get(0);
		assertEquals(serviceId, healthService.getService().getId());

		client.agentCheckWarn(checkId, "degraded");
		await("warning", () -> checkStatus() == CheckStatus.WARNING);
		// passing=true excludes services with warning checks
		await("not listed as passing", () -> healthServices(true).isEmpty());

		client.agentCheckFail(checkId, "down");
		await("critical", () -> checkStatus() == CheckStatus.CRITICAL);
		assertTrue(healthServices(true).isEmpty());
	}

	@Test
	void checksByStateContainServiceCheck() {
		registerServiceWithTtlCheck();
		client.agentCheckWarn(checkId);
		await("check listed in warning state", () -> client.getHealthChecksState(CheckStatus.WARNING, QueryParams.DEFAULT)
				.getValue().stream().anyMatch(c -> c.getCheckId().equals(checkId)));
		assertFalse(client.getHealthChecksState(CheckStatus.PASSING, QueryParams.DEFAULT).getValue().stream()
				.anyMatch(c -> c.getCheckId().equals(checkId)));
	}

	@Test
	void healthServicesFilteredByTag() {
		registerServiceWithTtlCheck("blue", "green");
		client.agentCheckPass(checkId);
		await("passing", () -> healthServices(true).size() == 1);

		HealthServicesRequest blue = new HealthServicesRequest.Builder()
				.setFilter(Filter.in("blue", Filter.Selector.of("Service.Tags"))).build();
		assertEquals(1, client.getHealthServices(serviceName, blue).getValue().size());
		HealthServicesRequest red = new HealthServicesRequest.Builder()
				.setFilter(Filter.in("red", Filter.Selector.of("Service.Tags"))).build();
		assertTrue(client.getHealthServices(serviceName, red).getValue().isEmpty());
	}

	@Test
	void healthServicesFilteredByTagWithReservedCharacters() {
		// +, & and = have a meaning in query strings and must be encoded
		String tag = "c++&x=y";
		registerServiceWithTtlCheck(tag);
		client.agentCheckPass(checkId);
		await("passing", () -> healthServices(true).size() == 1);

		HealthServicesRequest request = new HealthServicesRequest.Builder()
				.setFilter(Filter.in(tag, Filter.Selector.of("Service.Tags"))).build();
		assertEquals(1, client.getHealthServices(serviceName, request).getValue().size());
	}

	@Test
	void healthServicesFilteredByTagWithQuotesAndBackslash() {
		String quoted = "say \"hi\" C:\\temp";
		String backslash = "C:\\temp";
		registerServiceWithTtlCheck(quoted, backslash);
		client.agentCheckPass(checkId);
		await("passing", () -> healthServices(true).size() == 1);

		for (String tag : List.of(quoted, backslash)) {
			HealthServicesRequest request = new HealthServicesRequest.Builder()
					.setFilter(Filter.in(tag, Filter.Selector.of("Service.Tags"))).build();
			assertEquals(1, client.getHealthServices(serviceName, request).getValue().size(), tag);
		}
		HealthServicesRequest other = new HealthServicesRequest.Builder()
				.setFilter(Filter.in("C:\\other", Filter.Selector.of("Service.Tags"))).build();
		assertTrue(client.getHealthServices(serviceName, other).getValue().isEmpty());
	}

	@Test
	void serviceMaintenanceTakesServiceOutOfRotation() {
		registerServiceWithTtlCheck();
		client.agentCheckPass(checkId);
		await("passing", () -> healthServices(true).size() == 1);

		client.agentServiceSetMaintenance(serviceId, true, "deploying");
		await("out of rotation", () -> healthServices(true).isEmpty());
		List<String> checkIds = healthServices(false).get(0).getChecks().stream().map(Check::getCheckId)
				.collect(Collectors.toList());
		assertTrue(checkIds.contains("_service_maintenance:" + serviceId), checkIds.toString());

		client.agentServiceSetMaintenance(serviceId, false);
		await("back in rotation", () -> healthServices(true).size() == 1);
	}

	@Test
	void nodeMaintenanceTakesAllServicesOutOfRotation() {
		registerServiceWithTtlCheck();
		client.agentCheckPass(checkId);
		await("passing", () -> healthServices(true).size() == 1);
		try {
			client.agentSetMaintenance(true, "node maintenance");
			await("out of rotation", () -> healthServices(true).isEmpty());
		} finally {
			client.agentSetMaintenance(false);
		}
		await("back in rotation", () -> healthServices(true).size() == 1);
	}

	@Test
	void deregisteredServiceDisappears() {
		registerServiceWithTtlCheck();
		await("registered", () -> healthServices(false).size() == 1);

		client.agentServiceDeregister(serviceId);
		assertFalse(client.getAgentServices().getValue().containsKey(serviceId));
		await("removed from catalog", () -> healthServices(false).isEmpty());
	}
}
