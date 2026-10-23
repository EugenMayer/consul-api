package com.ecwid.consul.v1;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.MountableFile;

import com.ecwid.consul.ConsulTestContainers;
import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.agent.model.Service;

class ConsulClientTest {
	private static final int HTTPS_PORT = 8501;
	private static final String CERT_DIR = "/consul/certs";
	private ConsulContainer consul;

	@BeforeEach
	public void setup() {
		String certRootPath = CERT_DIR;
		String customConfiguration = "{\n" + "  \"datacenter\": \"dc-test\",\n" + "  \"log_level\": \"info\",\n"
				+ "  \"ports\": {\n" + "    \"https\": " + HTTPS_PORT + "\n" + "  },\n" + "  \"tls\": {\n"
				+ "    \"https\": {\n" + "      \"ca_file\": \"" + certRootPath + "/ca.cert\",\n"
				+ "      \"key_file\": \"" + certRootPath + "/key.key\",\n" + "      \"cert_file\": \"" + certRootPath
				+ "/key.crt\"\n" + "    }\n" + "  }\n" + "}\n";
		consul = ConsulTestContainers.newConsulContainer(customConfiguration);
		for (String file : new String[] { "ca.cert", "key.key", "key.crt" }) {
			consul.withCopyFileToContainer(MountableFile.forClasspathResource("ssl/" + file, 0644),
					CERT_DIR + "/" + file);
		}
		consul.addExposedPort(HTTPS_PORT);
		consul.start();
	}

	@AfterEach
	public void cleanup() {
		consul.close();
	}

	@Test
	void agentHttpTest() {
		String host = "http://" + consul.getHost();
		int port = consul.getMappedPort(8500);
		ConsulClient consulClient = new ConsulClient(host, port);
		serviceRegisterTest(consulClient);
	}

	/**
	 * Creates an {@link SSLContext} that trusts the test CA, which signed the
	 * certificate Consul serves (valid for localhost).
	 */
	private static SSLContext trustTestCa() throws Exception {
		KeyStore trustStore = KeyStore.getInstance("JKS");
		try (InputStream in = new FileInputStream("src/test/resources/ssl/trustStore.jks")) {
			trustStore.load(in, "change_me".toCharArray());
		}
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(trustStore);
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, tmf.getTrustManagers(), null);
		return sslContext;
	}

	@Test
	void agentHttpsTest() throws Exception {
		String host = "https://" + consul.getHost();
		int httpsPort = consul.getMappedPort(HTTPS_PORT);
		ConsulClient consulClient = new ConsulClient(host, httpsPort, trustTestCa());
		serviceRegisterTest(consulClient);
	}

	@Test
	void agentHttpsRejectsUntrustedCertificate() throws Exception {
		// the default trust store does not know the test CA
		String host = "https://" + consul.getHost();
		int httpsPort = consul.getMappedPort(HTTPS_PORT);
		ConsulClient consulClient = new ConsulClient(host, httpsPort, SSLContext.getDefault());
		assertThrows(TransportException.class, consulClient::getAgentServices);
	}

	private void serviceRegisterTest(ConsulClient consulClient) {
		NewService newService = new NewService();
		String serviceName = "abc";
		newService.setName(serviceName);
		consulClient.agentServiceRegister(newService);
		Response<Map<String, Service>> agentServicesResponse = consulClient.getAgentServices();
		Map<String, Service> services = agentServicesResponse.getValue();
		assertThat(services, IsMapContaining.hasKey(serviceName));
	}
}