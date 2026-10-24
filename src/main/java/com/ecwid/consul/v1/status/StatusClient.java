package com.ecwid.consul.v1.status;

import java.util.List;

import com.ecwid.consul.v1.Response;

/**
 * Client for the <a href="https://developer.hashicorp.com/consul/api-docs/status">status</a> endpoints: the Raft leader and peers.
 *
 * @author Vasily Vasilkov (vgv@ecwid.com)
 */
public interface StatusClient {
	Response<String> getStatusLeader();

	Response<List<String>> getStatusPeers();
}