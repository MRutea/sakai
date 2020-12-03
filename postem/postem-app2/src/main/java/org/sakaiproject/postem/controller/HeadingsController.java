package org.sakaiproject.postem.controller;

import java.util.List;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.postem.constants.PostemToolConstants;
import org.sakaiproject.postem.service.PostemSakaiService;
import org.sakaiproject.tool.api.SessionManager;

@Slf4j
@Controller
public class HeadingsController {
	
    @Autowired
    private PostemSakaiService postemSakaiService;
    
    @Autowired
	private SessionManager sessionManager;

	@GetMapping(value = {"/title/{ascendingTitle}"})
    public String sortByTitle(@PathVariable("ascendingTitle") boolean ascendingTitle, Model model) {
        log.debug("Postem sortByTitle");
        
        boolean toggleAscending = toggleSort(ascendingTitle);
		String userId = sessionManager.getCurrentSessionUserId();
		
		List<Gradebook> gradebooksList = postemSakaiService.getGradebooks(Gradebook.SORT_BY_TITLE, toggleAscending);
		
		model.addAttribute("gradebooksList", gradebooksList);
		model.addAttribute("sortedByTitle", "true");
		model.addAttribute("ascendingTitle", String.valueOf(toggleAscending));
		model.addAttribute("ascendingCreator", "false");
		model.addAttribute("ascendingModifiedBy", "false");
		model.addAttribute("ascendingLastMod", "false");
		model.addAttribute("ascendingReleased", "false");

        return PostemToolConstants.INDEX_TEMPLATE;
    }

	@GetMapping(value = {"/creator/{ascendingCreator}"})
    public String sortByCreator(@PathVariable("ascendingCreator") boolean ascendingCreator, Model model) {
        log.debug("Postem sortByCreator");
        
        boolean toggleAscending = toggleSort(ascendingCreator);
		String userId = sessionManager.getCurrentSessionUserId();
		
		List<Gradebook> gradebooksList = postemSakaiService.getGradebooks(Gradebook.SORT_BY_CREATOR, toggleAscending);
		
		model.addAttribute("gradebooksList", gradebooksList);
		model.addAttribute("sortedByCreator", "true");
		model.addAttribute("ascendingCreator", String.valueOf(toggleAscending));
		model.addAttribute("ascendingTitle", "false");
		model.addAttribute("ascendingModifiedBy", "false");
		model.addAttribute("ascendingLastMod", "false");
		model.addAttribute("ascendingReleased", "false");

        return PostemToolConstants.INDEX_TEMPLATE;
    }
    
	@GetMapping(value = {"/modifiedBy/{ascendingModifiedBy}"})
    public String sortByModifiedBy(@PathVariable("ascendingModifiedBy") boolean ascendingModifiedBy, Model model) {
        log.debug("Postem sortByModifiedBy");
        
        boolean toggleAscending = toggleSort(ascendingModifiedBy);
		String userId = sessionManager.getCurrentSessionUserId();
		
		List<Gradebook> gradebooksList = postemSakaiService.getGradebooks(Gradebook.SORT_BY_MOD_BY, toggleAscending);
		
		model.addAttribute("gradebooksList", gradebooksList);
		model.addAttribute("sortedByModifiedBy", "true");
		model.addAttribute("ascendingModifiedBy", String.valueOf(toggleAscending));
		model.addAttribute("ascendingTitle", "false");
		model.addAttribute("ascendingCreator", "false");
		model.addAttribute("ascendingLastMod", "false");
		model.addAttribute("ascendingReleased", "false");

        return PostemToolConstants.INDEX_TEMPLATE;
    }
    
	@GetMapping(value = {"/lastModified/{ascendingLastMod}"})
    public String sortByLastModified(@PathVariable("ascendingLastMod") boolean ascendingLastMod, Model model) {
        log.debug("Postem sortByLastModified");
        
        boolean toggleAscending = toggleSort(ascendingLastMod);
		String userId = sessionManager.getCurrentSessionUserId();
		
		List<Gradebook> gradebooksList = postemSakaiService.getGradebooks(Gradebook.SORT_BY_MOD_DATE, toggleAscending);
		
		model.addAttribute("gradebooksList", gradebooksList);
		model.addAttribute("sortedByLastModified", "true");
		model.addAttribute("ascendingLastMod", String.valueOf(toggleAscending));
		model.addAttribute("ascendingTitle", "false");
		model.addAttribute("ascendingCreator", "false");
		model.addAttribute("ascendingModifiedBy", "false");
		model.addAttribute("ascendingReleased", "false");

        return PostemToolConstants.INDEX_TEMPLATE;
    }

	@GetMapping(value = {"/released/{ascendingReleased}"})
    public String sortByReleased(@PathVariable("ascendingReleased") boolean ascendingReleased, Model model) {
        log.debug("Postem sortByReleased");
        
        boolean toggleAscending = toggleSort(ascendingReleased);
		String userId = sessionManager.getCurrentSessionUserId();
		
		List<Gradebook> gradebooksList = postemSakaiService.getGradebooks(Gradebook.SORT_BY_RELEASED, toggleAscending);
		
		model.addAttribute("gradebooksList", gradebooksList);
		model.addAttribute("sortedByReleased", "true");
		model.addAttribute("ascendingReleased", String.valueOf(toggleAscending));
		model.addAttribute("ascendingTitle", "false");
		model.addAttribute("ascendingCreator", "false");
		model.addAttribute("ascendingModifiedBy", "false");
		model.addAttribute("ascendingLastMod", "false");

        return PostemToolConstants.INDEX_TEMPLATE;
    }
    
	public boolean toggleSort(boolean ascending) {
	       if (ascending) {
	    	   ascending = false;
	       } else {
	    	   ascending = true;
	       }

	       return ascending;
	}
}
