/****************************************************************************** 
* Copyright (c) 2020 Apereo Foundation

* Licensed under the Educational Community License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at

*          http://opensource.org/licenses/ecl2

* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
 ******************************************************************************/
package org.sakaiproject.postem.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.api.app.postem.data.GradebookManager;
import org.sakaiproject.postem.constants.PostemToolConstants;
import org.sakaiproject.postem.service.PostemSakaiService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.util.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class MainController {
	
	public static final String messageBundle = "org.sakaiproject.tool.postem.bundle.Messages";

    @Autowired
    private PostemSakaiService postemSakaiService;
    
	@Inject
	private SessionManager sessionManager;
	
	@Inject
	private GradebookManager gradebookManager;

    @RequestMapping(value = {"/", "/index"})
    public String showIndex(Model model) {
        log.debug("showIndex()");
        
		String userId = sessionManager.getCurrentSessionUserId();
		
		List<Gradebook> gradebooksList = postemSakaiService.getGradebooks(Gradebook.SORT_BY_TITLE, true);
		
		model.addAttribute("gradebooksList", gradebooksList);
		model.addAttribute("sortedByTitle", "true");
		model.addAttribute("ascendingTitle", "true");
		model.addAttribute("ascendingCreator", "false");
		model.addAttribute("ascendingModifiedBy", "false");
		model.addAttribute("ascendingLastMod", "false");
		model.addAttribute("ascendingReleased", "false");		

        return PostemToolConstants.INDEX_TEMPLATE;
    }
    
    @RequestMapping(value = {"/add"})
    public String addItem(Model model) {
        log.debug("addItem()");
        
		String userId = sessionManager.getCurrentSessionUserId();
		String siteId = ToolManager.getCurrentPlacement().getContext();
		Gradebook currentGradebook = postemSakaiService.createEmptyGradebook(userId, siteId);
		currentGradebook.setTitle("");
		currentGradebook.setReleased(false);
  		model.addAttribute("gradebook", currentGradebook);
        return PostemToolConstants.ADD_ITEM;
    }
    
    @RequestMapping(value = "/uploadFile", method = RequestMethod.POST)
	public String submit(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
	    
		if (file.isEmpty()) {
			request.setAttribute("message",
			        "Please select a file to upload");
			return "uploadStatus";
		}
		
		request.getPathInfo();
		request.getContextPath();
		String tomcatBase = System.getProperty("catalina.base");

		try {
			byte[] bytes = file.getBytes();
			Path path = Paths.get("C:/Users/Public/"
			        + file.getOriginalFilename());
			Files.write(path, bytes);

			request.setAttribute("message",
			        "You have successfully uploaded '"
			                + file.getOriginalFilename() + "'");

		} catch (IOException e) {
			e.printStackTrace();
		}

		return PostemToolConstants.INDEX_TEMPLATE;
	}
    
    @RequestMapping(value = "/create_gradebook", method = RequestMethod.POST)
	public String createGradebook(@ModelAttribute Object gradebook, Model model) {
	    
		String userId = sessionManager.getCurrentSessionUserId();
		String siteId = ToolManager.getCurrentPlacement().getContext();
		Gradebook currentGradebook = postemSakaiService.createEmptyGradebook(userId, siteId);
		currentGradebook.setTitle("New grades01");
		currentGradebook.setReleased(false);
    	String result = postemSakaiService.processCreate(currentGradebook);
    	
  		model.addAttribute("gradebook", currentGradebook);
  		final ResourceLoader rb = new ResourceLoader(messageBundle);
    	switch (result) {
    	  case PostemToolConstants.DUPLICATE_TITLE: 
    		 model.addAttribute("errorMessage", rb.getFormattedMessage("duplicate_title"));
    		 return PostemToolConstants.ADD_ITEM; 
    	}
    	
		return PostemToolConstants.INDEX_TEMPLATE;
	}

}
