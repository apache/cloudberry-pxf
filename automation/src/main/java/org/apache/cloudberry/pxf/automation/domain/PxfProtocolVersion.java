package org.apache.cloudberry.pxf.automation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POJO for the VersionResource response; ignore extra fields so newer 404 JSON
 * payloads (timestamp/status/error/path) do not break deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PxfProtocolVersion {
	public String version;

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}
}
