package com.ecwid.consul.v1;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.util.Map;

import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.MountableFile;

import com.ecwid.consul.ConsulTestContainers;
import com.ecwid.consul.transport.TLSConfig;
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

	@SuppressWarnings("removal")
	@Test
	void agentHttpsTest() {
		String host = "https://" + consul.getHost();
		int httpsPort = consul.getMappedPort(HTTPS_PORT);
		String path = "src/test/resources/ssl";
		String certRootPath = new File(path).getAbsolutePath().replace('\\', '/');
		String certificatePath = certRootPath + "/trustStore.jks";
		char[] certificatePassword = "change_me".toCharArray();
		String keyStorePath = certRootPath + "/keyStore.jks";
		char[] keyStorePassword = "change_me".toCharArray();
		TLSConfig tlsConfig = new TLSConfig(TLSConfig.KeyStoreInstanceType.JKS, certificatePath, certificatePassword,
				keyStorePath, keyStorePassword);
		ConsulClient consulClient = new ConsulClient(host, httpsPort, tlsConfig);
		serviceRegisterTest(consulClient);
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