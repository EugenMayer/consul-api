package com.ecwid.consul.v1.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ecwid.consul.ConsulTestContainers;
import com.ecwid.consul.v1.ConsulRawClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.Node;
import org.testcontainers.consul.ConsulContainer;

class CatalogConsulClientTest {
	private ConsulContainer consul;
	private CatalogConsulClient consulClient;

	@BeforeEach
	void setUp() {
		consul = ConsulTestContainers.newConsulContainer();
		consul.start();
		consulClient = new CatalogConsulClient(
				new ConsulRawClient.Builder(consul.getHost(), consul.getMappedPort(8500)).build());
	}

	@AfterEach
	void tearDown() {
		consul.close();
	}

	@Test
	void testGetCatalogNodes() {
		CatalogNodesRequest request = CatalogNodesRequest.newBuilder().build();
		Response<List<Node>> response = consulClient.getCatalogNodes(request);

		// We should find only one node – this
		assertEquals(1, response.getValue().size());
	}
}