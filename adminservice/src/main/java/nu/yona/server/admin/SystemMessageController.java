/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import nu.yona.server.batch.client.BatchProxyService;

@Controller
@RequestMapping(value = "/systemMessages")
public class SystemMessageController
{
	@Autowired
	private BatchProxyService batchProxyService;

	@GetMapping(value = "/")
	public String getIndexPage()
	{
		return "system-messages";
	}

	@PostMapping(value = "/")
	public String addSystemMessage(@RequestParam String message, RedirectAttributes redirectAttributes)
	{
		batchProxyService.sendSystemMessage(message);

		redirectAttributes.addFlashAttribute("flashMessage", "System message sent successfully");
		return "redirect:/systemMessages/";
	}
}
