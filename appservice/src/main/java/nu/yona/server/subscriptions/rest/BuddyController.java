/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.rest.GoalController.GoalResourceAssembler;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.JsonRootLinkRelationProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.rest.BuddyController.BuddyResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.BuddyServiceException;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.TimeUtil;

@Controller
@ExposesResourceFor(BuddyResource.class)
@RequestMapping(value = "/users/{userId}/buddies", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BuddyController extends ControllerBase
{
	public static final String BUDDY_LINK = "buddy";

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurieProvider curieProvider;

	/**
	 * This method returns all the buddies that the given user has.
	 * 
	 * @param password The Yona password as passed on in the header of the request.
	 * @param userId The ID of the user. This is part of the URL.
	 * @return the list of buddies for the current user
	 */
	@GetMapping(value = "/")
	@ResponseBody
	public HttpEntity<CollectionModel<BuddyResource>> getAllBuddies(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(
					createAllBuddiesCollectionResource(curieProvider, userId, buddyService.getBuddiesOfUser(userId)),
					HttpStatus.OK);
		}
	}

	@GetMapping(value = "/{buddyId}")
	@ResponseBody
	public HttpEntity<BuddyResource> getBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID buddyId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{

			return createOkResponse(buddyService.getBuddy(buddyId), createResourceAssembler(userId));
		}
	}

	@PostMapping(value = "/")
	@ResponseBody
	public HttpEntity<BuddyResource> addBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody PostPutBuddyDto postPutBuddy)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createResponse(buddyService.addBuddyToRequestingUser(userId, convertToBuddy(postPutBuddy), this::getInviteUrl),
					HttpStatus.CREATED, createResourceAssembler(userId));
		}
	}

	@DeleteMapping(value = "/{buddyId}")
	@ResponseBody
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			buddyService.removeBuddy(userId, buddyId, Optional.ofNullable(messageStr));
		}
	}

	private BuddyDto convertToBuddy(PostPutBuddyDto postPutBuddy)
	{
		LinkRelation userRel = curieProvider.getNamespacedRelFor(BuddyDto.USER_REL);
		UserDto user = postPutBuddy.userInMap.get(userRel.value());
		if (user == null)
		{
			throw BuddyServiceException.missingUser(userRel);
		}
		return new BuddyDto(user, postPutBuddy.message, postPutBuddy.sendingStatus, postPutBuddy.receivingStatus,
				TimeUtil.utcNow());
	}

	public static CollectionModel<BuddyResource> createAllBuddiesCollectionResource(CurieProvider curieProvider, UUID userId,
			Set<BuddyDto> allBuddiesOfUser)
	{
		return new CollectionModel<>(new BuddyResourceAssembler(curieProvider, userId).toCollectionModel(allBuddiesOfUser),
				getAllBuddiesLinkBuilder(userId).withSelfRel());
	}

	static WebMvcLinkBuilder getAllBuddiesLinkBuilder(UUID userId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllBuddies(null, userId));
	}

	public String getInviteUrl(UUID newUserId, String tempPassword)
	{
		// getUserSelfLinkWithTempPassword should actually call expand, so we don't need to strip template parameters
		// This is not being done because of https://github.com/spring-projects/spring-hateoas/issues/703
		return stripTemplateParameters(UserController.getUserSelfLinkWithTempPassword(newUserId, tempPassword));
	}

	private String stripTemplateParameters(Link link)
	{
		String linkString = link.getHref();
		if (link.isTemplated())
		{
			return linkString.substring(0, linkString.indexOf('{'));
		}
		return linkString;
	}

	private BuddyResourceAssembler createResourceAssembler(UUID userId)
	{
		return new BuddyResourceAssembler(curieProvider, userId);
	}

	public static CollectionModel<GoalDto> createAllGoalsCollectionResource(UUID requestingUserId, UUID userId,
			Set<GoalDto> allGoalsOfUser)
	{
		return new CollectionModel<>(
				new GoalResourceAssembler(true, goalId -> getGoalLinkBuilder(requestingUserId, userId, goalId))
						.toCollectionModel(allGoalsOfUser),
				getAllGoalsLinkBuilder(requestingUserId, userId).withSelfRel());
	}

	public static WebMvcLinkBuilder getBuddyLinkBuilder(UUID userId, UUID buddyId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getBuddy(Optional.empty(), userId, buddyId));
	}

	public static WebMvcLinkBuilder getGoalLinkBuilder(UUID requestingUserId, UUID userId, UUID goalId)
	{
		return GoalController.getGoalLinkBuilder(requestingUserId, userId, goalId);
	}

	private static WebMvcLinkBuilder getAllGoalsLinkBuilder(UUID requestingUserId, UUID userId)
	{
		return GoalController.getAllGoalsLinkBuilder(requestingUserId, userId);
	}

	static class PostPutBuddyDto
	{
		private final Map<String, UserDto> userInMap;
		private final String message;
		private final Status sendingStatus;
		private final Status receivingStatus;

		@JsonCreator
		public PostPutBuddyDto(@JsonProperty(value = "_embedded", required = true) Map<String, UserDto> userInMap,
				@JsonProperty("message") String message,
				@JsonProperty(value = "sendingStatus", required = true) Status sendingStatus,
				@JsonProperty(value = "receivingStatus", required = true) Status receivingStatus)
		{
			this.userInMap = userInMap;
			this.message = message;
			this.sendingStatus = sendingStatus;
			this.receivingStatus = receivingStatus;
		}
	}

	static class BuddyResource extends EntityModel<BuddyDto>
	{
		private final CurieProvider curieProvider;
		private final UUID userId;

		public BuddyResource(CurieProvider curieProvider, UUID userId, BuddyDto buddy)
		{
			super(buddy);
			this.curieProvider = curieProvider;
			this.userId = userId;
		}

		@JsonProperty("_embedded")
		public Map<String, Object> getEmbeddedResources()
		{
			HashMap<String, Object> result = new HashMap<>();
			result.put(curieProvider.getNamespacedRelFor(BuddyDto.USER_REL).value(), UserController.UserResourceAssembler
					.createInstanceForBuddy(curieProvider, userId).toModel(getContent().getUser()));

			return result;
		}
	}

	static class BuddyResourceAssembler extends RepresentationModelAssemblerSupport<BuddyDto, BuddyResource>
	{
		private final UUID userId;
		private final CurieProvider curieProvider;

		public BuddyResourceAssembler(CurieProvider curieProvider, UUID userId)
		{
			super(BuddyController.class, BuddyResource.class);
			this.curieProvider = curieProvider;
			this.userId = userId;
		}

		@Override
		public BuddyResource toModel(BuddyDto buddy)
		{
			BuddyResource buddyResource = instantiateModel(buddy);
			WebMvcLinkBuilder selfLinkBuilder = getSelfLinkBuilder(buddy.getId());
			addSelfLink(selfLinkBuilder, buddyResource);
			addEditLink(selfLinkBuilder, buddyResource);
			if (buddy.getSendingStatus() == Status.ACCEPTED)
			{
				addDayActivityOverviewsLink(buddyResource);
				addWeekActivityOverviewsLink(buddyResource);
			}
			return buddyResource;
		}

		@Override
		protected BuddyResource instantiateModel(BuddyDto buddy)
		{
			return new BuddyResource(curieProvider, userId, buddy);
		}

		private WebMvcLinkBuilder getSelfLinkBuilder(UUID buddyId)
		{
			return getBuddyLinkBuilder(userId, buddyId);
		}

		private void addSelfLink(WebMvcLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(WebMvcLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withRel(JsonRootLinkRelationProvider.EDIT_REL));
		}

		private void addWeekActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(
					BuddyActivityController.getBuddyWeekActivityOverviewsLinkBuilder(userId, buddyResource.getContent().getId())
							.withRel(BuddyActivityController.WEEK_OVERVIEW_REL));
		}

		private void addDayActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(
					BuddyActivityController.getBuddyDayActivityOverviewsLinkBuilder(userId, buddyResource.getContent().getId())
							.withRel(BuddyActivityController.DAY_OVERVIEW_REL));
		}
	}
}
