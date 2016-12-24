/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;

@Entity
public class DisclosureRequestMessage extends BuddyMessage
{
	@ManyToOne
	private GoalConflictMessage targetGoalConflictMessage;
	private Status status;

	// Default constructor is required for JPA
	public DisclosureRequestMessage()
	{
	}

	private DisclosureRequestMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderUserNickname, String message)
	{
		super(senderUserId, senderUserAnonymizedId, senderUserNickname, message);
		this.status = Status.DISCLOSURE_REQUESTED;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return targetGoalConflictMessage;
	}

	public void setTargetGoalConflictMessage(GoalConflictMessage goalConflictMessage)
	{
		// TODO Auto-generated method stub

	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		super.encrypt(encryptor);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		super.decrypt(decryptor);
	}

	public static Message createInstance(UUID senderUserId, UUID senderUserAnonymizedId, String senderUserNickname,
			String message, GoalConflictMessage targetGoalConflictMessage)
	{
		DisclosureRequestMessage disclosureRequestMessage = new DisclosureRequestMessage(senderUserId, senderUserAnonymizedId,
				senderUserNickname, message);
		targetGoalConflictMessage.addDisclosureRequest(disclosureRequestMessage);
		return disclosureRequestMessage;
	}
}
