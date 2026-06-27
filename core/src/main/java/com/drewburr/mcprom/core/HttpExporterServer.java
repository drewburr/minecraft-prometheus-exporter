package com.drewburr.mcprom.core;

import java.io.IOException;

import io.prometheus.client.exporter.HTTPServer;

/**
 * Thin wrapper around the Prometheus {@link HTTPServer} that serves the default
 * registry on the configured address/port using a daemon thread, so platforms
 * don't each re-implement the lifecycle.
 */
public class HttpExporterServer {

	private HTTPServer server;

	/**
	 * Start the HTTP server.
	 *
	 * @param address The address to listen on.
	 * @param port The TCP port to listen on.
	 * @throws IOException If the server cannot bind.
	 */
	public HttpExporterServer(String address, int port) throws IOException {
		this.server = new HTTPServer(address, port, true);
	}

	/**
	 * Stop the HTTP server, if running.
	 */
	public void close() {
		if (this.server != null) {
			this.server.close();
			this.server = null;
		}
	}
}
