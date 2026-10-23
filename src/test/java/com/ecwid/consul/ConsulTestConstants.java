package com.ecwid.consul;

public class ConsulTestConstants {
	/**
	 * First iteration:
	 * 0.9.2 2017
	 * 1.0.0 2017
	 * 1.0.6 2018
	 * 1.4.0 2018 - Legacy ACL was deprecated
	 * 1.6.0 2019
	 * 1.11.0 2021 - Legacy ACL was removed
	 * 1.16.1 2023
	 * 1.18.1 2024
	 * 2.0.4 2026
	 *
	 * Override with -Dconsul.version=... to test against another Consul release.
	 */
	public static final String CONSUL_VERSION = System.getProperty("consul.version", "2.0.4");
	public static final String CONSUL_IMAGE = "hashicorp/consul:" + CONSUL_VERSION;
}