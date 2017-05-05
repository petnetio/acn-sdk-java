/*******************************************************************************
 * Copyright (c) 2017 Arrow Electronics, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution, and is available at
 * http://apache.org/licenses/LICENSE-2.0
 *
 * Contributors:
 *     Arrow Electronics, Inc.
 *******************************************************************************/
package com.arrow.acn.client.cloud;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import com.arrow.acn.client.ClientConstants;
import com.arrow.acn.client.IotParameters;
import com.arrow.acn.client.model.IbmConfigModel;
import com.arrow.acn.client.utils.Utils;
import com.arrow.acs.AcsLogicalException;
import com.ibm.iotf.client.gateway.Command;
import com.ibm.iotf.client.gateway.GatewayCallback;
import com.ibm.iotf.client.gateway.GatewayClient;
import com.ibm.iotf.client.gateway.Notification;

public class IbmConnector extends CloudConnectorAbstract {
	private static final String HEADER_ORGANIZATION_ID = "Organization-ID";
	private static final String HEADER_AUTHENTICATION_MODE = "Authentication-Method";
	private static final String HEADER_AUTHENTICATION_TOKEN = "Authentication-Token";
	private static final String HEADER_GATEWAY_TYPE = "Gateway-Type";
	private static final String HEADER_GATEWAY_ID = "Gateway-ID";
	private static final String HEADER_REGISTRATION_MODE = "Registration-Mode";
	private static final String VALUE_REGISTRATION_MODE = "Automatic";

	private static final String DEFAULT_EVENT = "telemetry";

	private final IbmConfigModel gatewayModel;
	private int qos;

	private GatewayClient gatewayClient;
	private DefaultGatewayCallback gatewayCallback = new DefaultGatewayCallback();

	public IbmConnector(IbmConfigModel gatewayModel) {
		this.gatewayModel = gatewayModel;
	}

	@Override
	public void start() {
		configureAndConnect();
	}

	private void configureAndConnect() {
		String method = "configureAndConnect";
		Properties props = new Properties();
		props.setProperty(HEADER_ORGANIZATION_ID, gatewayModel.getOrganizationId());
		props.setProperty(HEADER_AUTHENTICATION_MODE, gatewayModel.getAuthMethod());
		props.setProperty(HEADER_AUTHENTICATION_TOKEN, gatewayModel.getAuthToken());
		props.setProperty(HEADER_GATEWAY_ID, gatewayModel.getGatewayId());
		props.setProperty(HEADER_GATEWAY_TYPE, gatewayModel.getGatewayType());
		props.setProperty(HEADER_REGISTRATION_MODE, VALUE_REGISTRATION_MODE);
		try {
			gatewayClient = new GatewayClient(props);
			gatewayClient.setGatewayCallback(gatewayCallback);
			gatewayClient.connect(true);
		} catch (Exception e) {
			logError(method, "cannot connect to IBM Watson IoT", e);
		}
	}

	@Override
	public void stop() {
		gatewayClient.disconnect();
	}

	@Override
	public void send(IotParameters payload) {
		String method = "send";

		// check gateway client
		while (gatewayClient == null || !gatewayClient.isConnected()) {
			logError(method, "gatewayClient is not ready, check back in 5 seconds ...");
			Utils.sleep(ClientConstants.DEFAULT_CLOUD_SENDING_RETRY_MS);
		}

		if (!StringUtils.isEmpty(payload.getExternalId()) && !StringUtils.isEmpty(payload.getDeviceType())) {
			logDebug(method, "sending message to cloud via device: %s", payload.getExternalId());
			gatewayClient.publishDeviceEvent(payload.getDeviceType(), payload.getExternalId(), DEFAULT_EVENT, payload,
			        getQos());
		} else {
			logDebug(method, "sending message to cloud via gateway: %s", getGatewayHid());
			gatewayClient.publishGatewayEvent(DEFAULT_EVENT, payload, getQos());
		}
		logInfo(method, "message was sent successfully");
	}

	@Override
	public void sendBatch(List<IotParameters> batch, TransferMode transferMode) {
		if (transferMode == TransferMode.GZIP_BATCH) {
			throw new AcsLogicalException(
			        "TransferMode not supported for IBM integration: " + TransferMode.GZIP_BATCH.name());
		}
		if (batch != null) {
			batch.forEach(this::send);
		}
	}

	public int getQos() {
		return qos;
	}

	public void setQos(int qos) {
		this.qos = qos;
	}

	@Override
	public void setListener(MessageListener listener) {
		gatewayCallback.setListener(listener);
	}

	private static class DefaultGatewayCallback implements GatewayCallback {
		private MessageListener listener;

		DefaultGatewayCallback() {
		}

		@Override
		public void processCommand(Command cmd) {
			if (listener != null) {
				listener.processMessage(cmd.getCommand(), cmd.getRawPayload());
			}
		}

		@Override
		public void processNotification(Notification notification) {
			// not implemented in library
		}

		void setListener(MessageListener listener) {
			this.listener = listener;
		}
	}
}
