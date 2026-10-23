/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package com.ecwid.consul.transport;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.checkerframework.checker.nullness.qual.Nullable;


/**
 * Provides utility functions for creating {@link HttpTransport}s based on the
 * Java {@link HttpClient}.
 * 
 * @author Jon Huang
 *
 */
public final class ClientUtils {
	/**
	 * The default HTTP connect timeout (30 seconds).
	 */
	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
	/**
	 * The default HTTP request timeout (30 seconds).
	 */
	public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private ClientUtils() {
		// static utility class
	}

	public static DefaultHttpTransport createDefaultHttpTransport(@Nullable ProxySelector proxy,
			@Nullable SSLContext sslCtx, @Nullable SSLParameters sslParams, @Nullable Executor executor) {
		HttpClient.Builder builder = HttpClient.newBuilder().version(Version.HTTP_1_1) // consul supports HTTP 1.1
				.followRedirects(Redirect.NORMAL).connectTimeout(DEFAULT_CONNECT_TIMEOUT);
		if (proxy != null) {
			builder.proxy(proxy);
		}
		if (sslCtx != null) {
			builder.sslContext(sslCtx);
		}
		if (sslParams != null) {
			builder.sslParameters(sslParams);
		}
		if (executor != null) {
			builder.executor(executor);
		}
		return new DefaultHttpTransport(builder.build());
	}
}