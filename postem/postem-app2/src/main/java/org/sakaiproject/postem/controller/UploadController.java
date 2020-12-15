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

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.api.app.postem.data.StudentGrades;
import org.sakaiproject.postem.constants.PostemToolConstants;
import org.sakaiproject.postem.form.GradebookForm;
import org.sakaiproject.postem.service.PostemSakaiService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.PreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class UploadController {

	@Autowired
    private PostemSakaiService postemSakaiService;
    
	@Autowired
	private SessionManager sessionManager;
	
	@Autowired
	private MessageSource messageSource;
	
    @Autowired
    private PreferencesService preferencesService;
	
	private static final int TITLE_MAX_LENGTH = 255;
	private static final int HEADING_MAX_LENGTH = 500;

	@PostMapping(value = "/uploadFile")
	@ResponseStatus(value = HttpStatus.OK)
	public void uploadFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        log.debug("uploadFile");
    	String result = postemSakaiService.doDragDropUpload(file, request);
		ToolSession toolSession = sessionManager.getCurrentToolSession();
    	toolSession.setAttribute("resultUploading", result);
	}
    
    @PostMapping(value = "/create_gradebook")
	public String createGradebook(@ModelAttribute("gradebookForm") GradebookForm gradebookForm, Model model) {
        log.debug("createGradebook");
        
	    String userId = sessionManager.getCurrentSessionUserId();
		String visible = Boolean.toString(postemSakaiService.checkAccess());
		model.addAttribute("visible", visible);	
	    final Locale locale = StringUtils.isNotBlank(userId) ? preferencesService.getLocale(userId) : Locale.getDefault();
		ToolSession toolSession = sessionManager.getCurrentToolSession();
        Gradebook sessionGradebook = (Gradebook) toolSession.getAttribute("currentGradebook");
		String siteId = ToolManager.getCurrentPlacement().getContext();
		Gradebook currentGradebook = postemSakaiService.createEmptyGradebook(userId, siteId);
		
	    //wait until file upload completes
	    try {
	        Thread.sleep(1000);
	    } catch(InterruptedException ex) {
	        Thread.currentThread().interrupt();
	    }
	    
		String resultUploading = (String) toolSession.getAttribute("resultUploading");
		String literalErrorMessage = null;
	    String fileId = (String) toolSession.getAttribute("attachmentId");
	    String[] parts = fileId.split("/");
	    String partFileReference = parts[parts.length-1];
		gradebookForm.setFileReference(partFileReference);
  		model.addAttribute("fileReference", partFileReference);
  		
		if (null != resultUploading && resultUploading != PostemToolConstants.RESULT_OK) {
			switch (resultUploading) {
			  case PostemToolConstants.NAME_FILE_TOO_LONG: 
				  literalErrorMessage = MessageFormat.format(messageSource.getMessage(PostemToolConstants.NAME_FILE_TOO_LONG, null, locale), 
					TITLE_MAX_LENGTH);
				  break;
			}
			
			model.addAttribute("literalErrorMessage", literalErrorMessage);
			return PostemToolConstants.ADD_ITEM;
		}
		
		if (null != sessionGradebook && (null == fileId || fileId.isEmpty())) {
			fileId = sessionGradebook.getFileReference();
			toolSession.setAttribute("attachmentId", fileId);
		}
		if (fileId == null || fileId.isEmpty()) {
			model.addAttribute("errorMessage", PostemToolConstants.MISSING_CSV);
			return PostemToolConstants.ADD_ITEM;
		}	
	    
        if (null == sessionGradebook || !sessionGradebook.getTitle().equals(gradebookForm.getTitle()) 
        		|| sessionGradebook.getReleased() != gradebookForm.isReleased() || !sessionGradebook.getFileReference().equals(fileId)) {
			
			boolean isGradebookUpdate = gradebookForm.isGradebookUpdate();
			
			currentGradebook.setRelease(gradebookForm.isReleased());
			currentGradebook.setTitle(gradebookForm.getTitle());
			currentGradebook.setFileReference(fileId);
			currentGradebook.setId(gradebookForm.getId());
		
			String result = postemSakaiService.processCreate(currentGradebook, isGradebookUpdate);
			
			switch (result) {
			  case PostemToolConstants.EMPTY_FILE: 
				 model.addAttribute("errorMessage", PostemToolConstants.EMPTY_FILE);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.BLANK_ROWS: 
				 model.addAttribute("errorMessage", PostemToolConstants.BLANK_ROWS);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.HAS_DUPLICATE_USERNAME: 
				 literalErrorMessage = MessageFormat.format(messageSource.getMessage(PostemToolConstants.HAS_DUPLICATE_USERNAME, null, locale), 
						 postemSakaiService.getDuplicateUserNames());
				 model.addAttribute("literalErrorMessage", literalErrorMessage);
				 return PostemToolConstants.ADD_ITEM;	
			  case PostemToolConstants.GENERIC_UPLOAD_ERROR: 
				 model.addAttribute("errorMessage", PostemToolConstants.GENERIC_UPLOAD_ERROR);
				 return PostemToolConstants.ADD_ITEM;			
			  case PostemToolConstants.CSV_DELETE_FAIL: 
				 model.addAttribute("errorMessage", PostemToolConstants.CSV_DELETE_FAIL);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.CSV_WITHOUT_STUDENTS: 
				 model.addAttribute("errorMessage", PostemToolConstants.CSV_WITHOUT_STUDENTS);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.DUPLICATE_TITLE: 
				 model.addAttribute("errorMessage", PostemToolConstants.DUPLICATE_TITLE);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.MISSING_TITLE: 
				 model.addAttribute("errorMessage", PostemToolConstants.MISSING_TITLE);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.TITLE_TOO_LONG: 
				 literalErrorMessage = MessageFormat.format(messageSource.getMessage(PostemToolConstants.TITLE_TOO_LONG, null, locale), 
						 new Integer(currentGradebook.getTitle().trim().length()), TITLE_MAX_LENGTH);
				 model.addAttribute("literalErrorMessage", literalErrorMessage);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.MISSING_CSV: 
				 model.addAttribute("errorMessage", PostemToolConstants.MISSING_CSV);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.PERMISSION_ERROR:
				 model.addAttribute("errorMessage", PostemToolConstants.PERMISSION_ERROR);
				 return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.INVALID_EXT:
				 literalErrorMessage = MessageFormat.format(messageSource.getMessage(PostemToolConstants.INVALID_EXT, null, locale), 
						  partFileReference, TITLE_MAX_LENGTH);
				  break;
			  case PostemToolConstants.BLANK_HEADINGS:
				 model.addAttribute("errorMessage", PostemToolConstants.BLANK_HEADINGS);
					return PostemToolConstants.ADD_ITEM;
			  case PostemToolConstants.HEADING_TOO_LONG:
				 literalErrorMessage = MessageFormat.format(messageSource.getMessage(PostemToolConstants.HEADING_TOO_LONG, null, locale), 
						  new Integer(HEADING_MAX_LENGTH));
				 break;
			  case PostemToolConstants.USER_NAME_INVALID:
			     model.addAttribute("errorMessage", PostemToolConstants.USER_NAME_INVALID);
				return PostemToolConstants.ADD_ITEM;
			}
        } else {
        	currentGradebook = sessionGradebook;
        }
    	
    	//to populate verify view
		String hasStudents = MessageFormat.format(messageSource.getMessage(PostemToolConstants.HAS_STUDENTS, null, locale), 
				new Integer(currentGradebook.getStudents().size()));
		model.addAttribute("has_students", hasStudents);
		
    	StudentGrades studentGrades = currentGradebook.studentGrades(currentGradebook.getFirstUploadedUsername());
    	model.addAttribute("studentGrades", studentGrades);
		model.addAttribute("currentGradebook", currentGradebook);
  		model.addAttribute("gradebookForm", gradebookForm);
		toolSession.setAttribute("currentGradebook", currentGradebook);
		
		return PostemToolConstants.VERIFY;
	}

    @PostMapping(value = "/create_gradebook_ok")
	public String createGradebookOk(Model model) {
        log.debug("createGradebook");
        
		ToolSession toolSession = sessionManager.getCurrentToolSession();
        Gradebook gradebook = (Gradebook) toolSession.getAttribute("currentGradebook");
        if (null != gradebook) {
        	postemSakaiService.processCreateOk(gradebook);
        }
        
		toolSession.setAttribute("currentGradebook", null);
		toolSession.setAttribute("attachmentId", "");
		
		return PostemToolConstants.REDIRECT_MAIN_TEMPLATE;
    }

}
