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

import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.postem.constants.PostemToolConstants;
import org.sakaiproject.postem.form.GradebookForm;
import org.sakaiproject.postem.service.PostemSakaiService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.PreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class MainController {

    @Autowired
    private PostemSakaiService postemSakaiService;
	
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private PreferencesService preferencesService;

    @RequestMapping(value = {"/", "/index"})
    public String showIndex(Model model, HttpServletRequest request, HttpServletResponse response) {
        log.debug("showIndex()");
        
        String userId = sessionManager.getCurrentSessionUserId();
        final Locale locale = StringUtils.isNotBlank(userId) ? preferencesService.getLocale(userId) : Locale.getDefault();
        LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request);
        localeResolver.setLocale(request, response, locale);
		
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
		currentGradebook.setTitle("xxxxxxxx");
		currentGradebook.setReleased(false);
		GradebookForm gradebookForm = new GradebookForm();
  		model.addAttribute("gradebookForm", gradebookForm);
  		model.addAttribute("gradebook", currentGradebook);
        return PostemToolConstants.ADD_ITEM;
    }
   
}
