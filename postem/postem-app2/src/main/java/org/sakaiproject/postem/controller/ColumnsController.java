package org.sakaiproject.postem.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.servlet.ServletContext;

import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.api.app.postem.data.StudentGrades;
import org.sakaiproject.postem.constants.PostemToolConstants;
import org.sakaiproject.postem.form.GradebookForm;
import org.sakaiproject.postem.helpers.CSV;
import org.sakaiproject.postem.helpers.MediaTypeUtils;
import org.sakaiproject.postem.helpers.Pair;
import org.sakaiproject.postem.service.PostemSakaiService;
import org.sakaiproject.tool.api.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ColumnsController {
	
	protected ArrayList students;
	protected Pair pair;
	protected Gradebook currentGradebook;
	protected TreeMap studentMap;
	
    @Autowired
    private PostemSakaiService postemSakaiService;
    
    @Autowired
	private SessionManager sessionManager;
	
	@Autowired
	ServletContext context; 

	@GetMapping(value = {"/gradebook_view/{gradebookId}"})
    public String getViewGradebook(@PathVariable("gradebookId") Long gradebookId, Model model) {
        log.debug("getViewGradebook");
        
        String userId = sessionManager.getCurrentSessionUserId();
        pair = postemSakaiService.processInstructorView(gradebookId);		
        if(pair.getFirst().toString().equals(PostemToolConstants.PERMISSION_ERROR) && pair.getSecond()==null) {
          return PostemToolConstants.PERMISSION_ERROR;
        }		
        currentGradebook = (Gradebook) pair.getFirst();
        students = (ArrayList) pair.getSecond();
        model.addAttribute("currentGradebook", currentGradebook);
        model.addAttribute("studentsList", students);

        return PostemToolConstants.GRADEBOOK_VIEW;
    }
    
	@GetMapping(value = {"/student_view/{gradebookId}"})
    public String getViewStudent(@PathVariable("gradebookId") Long gradebookId, Model model) {
        log.debug("getViewStudent");
        
		String userId = sessionManager.getCurrentSessionUserId();
		studentMap = postemSakaiService.processGradebookView(gradebookId);

		model.addAttribute("studentMap", studentMap);
		model.addAttribute("gradebookId", gradebookId);

        return PostemToolConstants.STUDENT_VIEW;
    }
    
	@GetMapping(value = {"/student_view_result/{gradebookId}/{student}"})
    public String getViewStudentResult(@PathVariable("gradebookId") Long gradebookId, @PathVariable("student") String selectedStudent, Model model) {
        log.debug("getViewStudent");
        
		String userId = sessionManager.getCurrentSessionUserId();
		studentMap = postemSakaiService.processGradebookView(gradebookId);
		Pair pair = postemSakaiService.getGradebookById(gradebookId);
        if(pair.getFirst()!=null && pair.getFirst().toString().equals(PostemToolConstants.PERMISSION_ERROR) && pair.getSecond()==null) {
            return PostemToolConstants.PERMISSION_ERROR;
          }		
          currentGradebook = (Gradebook) pair.getSecond();
		StudentGrades selStudent = null;
		String lastSelected = "";
		
		if(selectedStudent!=null) {		
			selStudent = postemSakaiService.getStudentByGBAndUsername(currentGradebook, selectedStudent);
			selStudent.setGradebook(currentGradebook);
		if (selStudent != null) {
			lastSelected = selStudent.getUsername();
		} 
		} 
		model.addAttribute("lastSelected", lastSelected);
		model.addAttribute("selStudent", selStudent);
		model.addAttribute("studentMap", studentMap);
		model.addAttribute("gradebookId", gradebookId);

        return PostemToolConstants.STUDENT_VIEW;
    }
    
	@GetMapping(value = {"/delete_confirm/{gradebookId}"})
    public String getDeleteConfirm(@PathVariable("gradebookId") Long gradebookId, Model model) {
        log.debug("getDeleteConfirm");
        
		String userId = sessionManager.getCurrentSessionUserId();
		pair = postemSakaiService.processGradebookDelete(gradebookId);
		
		if(pair.getFirst().toString().equals(PostemToolConstants.PERMISSION_ERROR) && pair.getSecond()==null) {
			return PostemToolConstants.PERMISSION_ERROR;
		}
		currentGradebook = (Gradebook) pair.getFirst();
		model.addAttribute("currentGradebook", currentGradebook);

        return PostemToolConstants.DELETE_CONFIRM;
    }
    
	@GetMapping(value = {"/processDelete/{gradebookId}"})
    public String processDelete(@PathVariable("gradebookId") Long gradebookId, Model model) {
        log.debug("processDelete");
        
		String userId = sessionManager.getCurrentSessionUserId();
		String result = postemSakaiService.processDelete(gradebookId);
		
		if(result.equals(PostemToolConstants.RESULT_KO)) {
			return PostemToolConstants.PERMISSION_ERROR;
		}

        return PostemToolConstants.REDIRECT_MAIN_TEMPLATE;
    }
    
	@GetMapping(value = {"/process_csv_download/{gradebookId}"})
    public ResponseEntity<InputStreamResource> processCsvDownload(@PathVariable("gradebookId") Long gradebookId, Model model) {
        log.debug("processCsvDownload()");
        
		Pair pair = postemSakaiService.processCsvDownload(gradebookId);
		
		if(pair.getFirst().toString().equals(PostemToolConstants.PERMISSION_ERROR) && pair.getSecond()==null) {
			return null;
		}
		
		CSV csv = (CSV) pair.getFirst();
    	String fileName =  (String) pair.getSecond();
    			
        MediaType mediaType = MediaTypeUtils.getMediaTypeForFileName(context, "postem_" + fileName + ".csv");
 
        File file = new File("postem_" + fileName + ".csv");
        InputStreamResource resource = null;
		PrintStream out = null;
		try {
			
			out = new PrintStream(new FileOutputStream(file));
			out.print(csv.getCsv());
			
			resource = new InputStreamResource(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
 
        return ResponseEntity.ok()
                // Content-Disposition
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + file.getName())
                // Content-Type
                .contentType(mediaType)
                // Contet-Length
                .contentLength(file.length()) //
                .body(resource);

    }
    
	@GetMapping(value = {"/gradebook_update/{gradebookId}"})
    public String getGradebookUpdate(@PathVariable("gradebookId") Long gradebookId, Model model) {
        log.debug("getGradebookUpdate");
        String userId = sessionManager.getCurrentSessionUserId();
		Pair pair = postemSakaiService.getGradebookById(gradebookId);
        if(pair.getFirst()!= null && pair.getFirst().toString().equals(PostemToolConstants.PERMISSION_ERROR) && pair.getSecond()==null) {
            return PostemToolConstants.PERMISSION_ERROR;
          }		
        currentGradebook = (Gradebook) pair.getSecond();
        String fileReference = currentGradebook.getFileReference();
        String[] parts = fileReference.split("/");
        String partFileReference = parts[parts.length-1];
  		
		GradebookForm gradebookForm = new GradebookForm();
		model.addAttribute("fileReference", partFileReference);
		gradebookForm.setReleased(currentGradebook.getRelease());
		gradebookForm.setTitle(currentGradebook.getTitle());
		gradebookForm.setFileReference(partFileReference);
		gradebookForm.setId(currentGradebook.getId());
  		model.addAttribute("gradebookForm", gradebookForm);
        return PostemToolConstants.ADD_ITEM;
    }
   
}
