package com.ecwid.consul;

import org.testcontainers.consul.ConsulContainer;

/**
 * Creates Consul dev agents in Docker for integration tests.
 */
public final class ConsulTestContainers {
	private ConsulTestContainers() {
		// static utility class
	}

	public static ConsulContainer newConsulContainer() {
		return new ConsulContainer(ConsulTestConstants.CONSUL_IMAGE);
	}

	/**
	 * @param localConfig Consul agent configuration as JSON, passed through the
	 *                    image's {@code CONSUL_LOCAL_CONFIG} variable.
	 */
	public static ConsulContainer newConsulContainer(String localConfig) {
		return newConsulContainer().withEnv("CONSUL_LOCAL_CONFIG", localConfig);
	}
}
