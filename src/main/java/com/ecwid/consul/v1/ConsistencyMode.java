package com.ecwid.consul.v1;

/**
 * The <a href="https://developer.hashicorp.com/consul/api-docs/features/consistency">consistency mode</a> of a read request.
 *
 * @author Vasily Vasilkov (vgv@ecwid.com)
 */
public enum ConsistencyMode {
	DEFAULT, STALE, CONSISTENT
}