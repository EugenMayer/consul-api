package com.ecwid.consul.v1.acl.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

public final class AclNodeIdentity {
	@JsonProperty("NodeName")
	@SerializedName("NodeName")
	private String nodeName;
	@JsonProperty("Datacenter")
	@SerializedName("Datacenter")
	private String datacenter;

	public AclNodeIdentity() {
	}

	public AclNodeIdentity(String nodeName, String datacenter) {
		this.nodeName = nodeName;
		this.datacenter = datacenter;
	}

	public final String getNodeName() {
		return nodeName;
	}

	public final void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public final String getDatacenter() {
		return datacenter;
	}

	public final void setDatacenter(String datacenter) {
		this.datacenter = datacenter;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(datacenter, nodeName);
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof AclNodeIdentity)) {
			return false;
		}
		AclNodeIdentity other = (AclNodeIdentity) obj;
		return Objects.equals(datacenter, other.datacenter) && Objects.equals(nodeName, other.nodeName);
	}

	@Override
	public final String toString() {
		return "AclNodeIdentity [nodeName=" + nodeName + ", datacenter=" + datacenter + "]";
	}
}