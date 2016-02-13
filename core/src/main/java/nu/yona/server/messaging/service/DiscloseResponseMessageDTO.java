/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.messaging.entities.DiscloseResponseMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.BuddyMessageDTO;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("discloseResponseMessage")
public class DiscloseResponseMessageDTO extends BuddyMessageDTO
{
	private Status status;

	private DiscloseResponseMessageDTO(UUID id, Date creationTime, UserDTO user, Status status, String nickname, String message)
	{
		super(id, creationTime, user, nickname, message);
		this.status = status;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		return possibleActions;
	}

	public Status getStatus()
	{
		return status;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static DiscloseResponseMessageDTO createInstance(UserDTO actingUser, DiscloseResponseMessage messageEntity)
	{
		return new DiscloseResponseMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), actingUser,
				messageEntity.getStatus(), messageEntity.getNickname(), messageEntity.getMessage());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(DiscloseResponseMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return DiscloseResponseMessageDTO.createInstance(actingUser, (DiscloseResponseMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				default:
					throw MessageServiceException.actionNotSupported(action);
			}
		}
	}
}
