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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.postem.constants.PostemToolConstants;
import org.sakaiproject.postem.form.GradebookForm;
import org.sakaiproject.postem.service.PostemSakaiService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.cover.ToolManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class UploadController {

    @Autowired
    private PostemSakaiService postemSakaiService;
    
	@Inject
	private SessionManager sessionManager;
	
	private static final int TITLE_MAX_LENGTH = 255;

	@PostMapping(value = "/uploadFile")
	public void uploadFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        log.debug("uploadFile");
    	String result = postemSakaiService.doDragDropUpload(file, request);
		ToolSession toolSession = sessionManager.getCurrentToolSession();
    	toolSession.setAttribute("resultUploading", result);
	}
    
    @PostMapping(value = "/create_gradebook")
	public String createGradebook(@ModelAttribute("gradebookForm") GradebookForm gradebookForm, Model model) {
        log.debug("createGradebook");
        //wait until file upload
        try {
            Thread.sleep(1000);
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
		String userId = sessionManager.getCurrentSessionUserId();
		String siteId = ToolManager.getCurrentPlacement().getContext();
		Gradebook currentGradebook = postemSakaiService.createEmptyGradebook(userId, siteId);
		currentGradebook.setTitle("");
		currentGradebook.setReleased(false);

		System.out.println(gradebookForm);
  		model.addAttribute("gradebook", currentGradebook);
  		
		ToolSession toolSession = sessionManager.getCurrentToolSession();
		toolSession.setAttribute("file", "");
		
		String resultUploading = (String) toolSession.getAttribute("resultUploading");
		if (resultUploading != PostemToolConstants.RESULT_OK) {
			model.addAttribute("errorMessage", PostemToolConstants.ERROR_UPLOADING);
   			return PostemToolConstants.ADD_ITEM;
		}
		String fileId = (String) toolSession.getAttribute("file");
		if (fileId.isEmpty() || fileId==null) {
			model.addAttribute("errorMessage", PostemToolConstants.MISSING_CSV);
   			return PostemToolConstants.ADD_ITEM;
		}		

    	String result = postemSakaiService.processCreate(currentGradebook);
    	
    	switch (result) {
    	  case PostemToolConstants.DUPLICATE_TITLE: 
    		 model.addAttribute("errorMessage", PostemToolConstants.DUPLICATE_TITLE);
    		 return PostemToolConstants.ADD_ITEM;
    	  case PostemToolConstants.MISSING_TITLE: 
    		 model.addAttribute("errorMessage", PostemToolConstants.MISSING_TITLE);
    		 return PostemToolConstants.ADD_ITEM;
    	  case PostemToolConstants.TITLE_TOO_LONG: 
    		 model.addAttribute("errorMessage", PostemToolConstants.TITLE_TOO_LONG);
    		 return PostemToolConstants.ADD_ITEM;
    	  case PostemToolConstants.MISSING_CSV: 
    		 model.addAttribute("errorMessage", PostemToolConstants.MISSING_CSV);
    		 return PostemToolConstants.ADD_ITEM;
    	}
    	
		return PostemToolConstants.INDEX_TEMPLATE;
	}

}
