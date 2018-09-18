/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;

@Service
public class FirebaseService
{
	private static final Logger logger = LoggerFactory.getLogger(FirebaseService.class);

	@Value("/api.json")
	Resource apiKey;

	@Autowired
	private YonaProperties yonaProperties;

	@PostConstruct
	private void init()
	{
		try (InputStream serviceAccount = apiKey.getInputStream())
		{
			FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.setDatabaseUrl(yonaProperties.getFirebaseDatabaseUrl()).build();

			FirebaseApp.initializeApp(options);
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public void sendMessage(String registrationToken, nu.yona.server.messaging.entities.Message message)
	{
		// It is hard to build the message URL here, as this is done by the app service,
		// and not known to other services which also do message sending
		Message firebaseMessage = Message.builder().putData("messageId", Long.toString(message.getId()))
				.setToken(registrationToken).build();

		try
		{
			FirebaseMessaging.getInstance().send(firebaseMessage);
		}
		catch (FirebaseMessagingException e)
		{
			logger.error("Error sending message to Firebase server", e);
		}
	}
}
