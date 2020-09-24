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
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.postem.constants.PostemToolConstants;
import org.sakaiproject.postem.service.PostemSakaiService;
import org.sakaiproject.tool.api.SessionManager;

@Slf4j
@Controller
public class MainController {

    @Autowired
    private PostemSakaiService postemSakaiService;
    
	@Inject
	private SessionManager sessionManager;

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
        return PostemToolConstants.ADD_ITEM;
    }
    
    @RequestMapping(value = {"/processAddAttachRedirect"})
    public String processAddAttachRedirect(Model model) {
        log.debug("processAddAttachRedirect()");
        
		String userId = sessionManager.getCurrentSessionUserId();
		postemSakaiService.processAddAttachRedirect();
		return PostemToolConstants.ADD_ITEM;
    }

}
