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
package org.sakaiproject.postem.service;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.DataFormatException;

import javax.inject.Inject;

import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.api.app.postem.data.GradebookManager;
import org.sakaiproject.api.app.postem.data.StudentGrades;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.FilePickerHelper;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.postem.constants.PostemToolConstants;

import org.sakaiproject.postem.helpers.Pair;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.postem.helpers.CSV;
import org.sakaiproject.site.cover.SiteService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PostemSakaiService  {
	
	protected ArrayList gradebooks;
	protected String userId;
	protected String userEid;
	protected String siteId = null;
	protected boolean ascending = true;	
	protected String sortBy = Gradebook.SORT_BY_TITLE;
	protected Gradebook currentGradebook;
	protected ArrayList students;
	private Boolean editable;
	protected TreeMap studentMap;
	protected String csv = null;
	private List filePickerList;
	private Reference attachment;
	protected String newTemplate;
	
	@Inject
	private SessionManager sessionManager;
	
	@Inject
	private UserDirectoryService userDirectoryService;
	
	@Inject
	private GradebookManager gradebookManager;

	public ArrayList getGradebooks(String sortBy, boolean ascending) {
		if (userId == null) {
			userId = sessionManager.getCurrentSessionUserId();
			
			if (userId != null) {
				try {
					userEid = userDirectoryService.getUserEid(userId);
				} catch (UserNotDefinedException e) {
					log.error("UserNotDefinedException", e);
				}
			}
		}

		Placement placement = ToolManager.getCurrentPlacement();
		String currentSiteId = placement.getContext();

		siteId = currentSiteId;
		try {
			if (checkAccess()) {
				// logger.info("**** Getting by context!");
				gradebooks = new ArrayList(gradebookManager
						.getGradebooksByContext(siteId, sortBy, ascending));
			} else {
				// logger.info("**** Getting RELEASED by context!");
				gradebooks = new ArrayList(gradebookManager
						.getReleasedGradebooksByContext(siteId, sortBy, ascending));
			}
		} catch (Exception e) {
			gradebooks = null;
		}	
		
		return gradebooks;

	}
	
	public Pair processInstructorView(Long gradebookId) {
		try {
			if (!this.checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			// logger.info(this + ".getEntries() in PostemTool " + e);
			return new Pair(PostemToolConstants.PERMISSION_ERROR, null);
		}
		Gradebook currentGradebook = gradebookManager.getGradebookByIdWithHeadingsAndStudents(gradebookId);

		students = new ArrayList(currentGradebook.getStudents());
		
		Pair pair = new Pair(currentGradebook, students);

		return pair;
	}
	
	public TreeMap processGradebookView(Long gradebookId) {
		if (isEditable()) {
			currentGradebook = gradebookManager.getGradebookByIdWithHeadings(gradebookId);
			currentGradebook.setUsernames(gradebookManager.getUsernamesInGradebook(currentGradebook));
			studentMap = currentGradebook.getStudentMap();
			//setSelectedStudent((String) studentMap.firstKey());
			return studentMap;
		}
		
		// otherwise, just load what we need for the current user
		currentGradebook = gradebookManager.getGradebookByIdWithHeadings(gradebookId);
		this.userId = sessionManager.getCurrentSessionUserId();
		
		return studentMap;
	}
	
	public Pair processGradebookDelete(Long gradebookId) {
		try {
			if (!this.checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			// logger.info(this + ".getEntries() in PostemTool " + e);
			return new Pair(PostemToolConstants.PERMISSION_ERROR, null);
		}
		Gradebook currentGradebook = gradebookManager.getGradebookByIdWithHeadingsAndStudents(gradebookId);
		
		Pair pair = new Pair(currentGradebook, null);

		return pair;
		
	}
	
	public String processDelete(Long gradebookId) {
		try {
			if (!this.checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			// logger.info(this + ".getEntries() in PostemTool " + e);
			return "ko";
		}
		Gradebook currentGradebook = gradebookManager.getGradebookByIdWithHeadingsAndStudents(gradebookId);
		gradebookManager.deleteGradebook(currentGradebook);
		return "ok";
		
	}
	
	public Pair processCsvDownload(Long gradebookId) {
		try {
			if (!this.checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			// logger.info(this + ".getEntries() in PostemTool " + e);
			return new Pair(PostemToolConstants.PERMISSION_ERROR, null);
		}
		currentGradebook = gradebookManager.getGradebookByIdWithHeadingsAndStudents(gradebookId);

		List csvContents = new ArrayList();
		if (currentGradebook.getHeadings().size() > 0) {
			csvContents.add(currentGradebook.getHeadings());
		}
		Iterator si = currentGradebook.getStudents().iterator();
		while (si.hasNext()) {
			List sgl = new ArrayList();
			StudentGrades sg = (StudentGrades) si.next();
			sgl.add(sg.getUsername());
			sgl.addAll(sg.getGrades());
			csvContents.add(sgl);
		}

		CSV newCsv = new CSV(csvContents, currentGradebook.getHeadings().size() > 0);
		String fileName = currentGradebook.getTitle();
		Pair pair = new Pair(newCsv, fileName);
		return pair;
	}
	
  public boolean isEditable() {
    if (editable == null) {
      editable = checkAccess();
    }
    return editable;
  }

  public boolean checkAccess() {
    return SiteService.allowUpdateSite(ToolManager.getCurrentPlacement().getContext());
  }

  public Pair getGradebookById(Long gradebookId) {
    try {
      if (!this.checkAccess()) {
        throw new PermissionException(sessionManager.getCurrentSessionUserId(),
          "syllabus_access_athz", "");
      }
   } catch (PermissionException e) {
  return new Pair(PostemToolConstants.PERMISSION_ERROR, null);
}
      Gradebook currentGradebook = gradebookManager.getGradebookByIdWithHeadingsAndStudents(gradebookId);
      Pair pair = new Pair(null, currentGradebook);
      return pair;
  }
	
  public StudentGrades getStudentByGBAndUsername(Gradebook currentGradebook, String selectedStudent) {
    StudentGrades selStudent = gradebookManager.getStudentByGBAndUsername(currentGradebook, selectedStudent);
    return selStudent;
  }
  
  public Gradebook createEmptyGradebook(String creator, String context) {
	    Gradebook gradebook = gradebookManager.createEmptyGradebook(creator, context);
	    return gradebook;
	  }
  
	public String processCreate(Gradebook gradebook) {

		try {
			if (!this.checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			this.currentGradebook = null;
			this.csv = null;
			this.newTemplate = null;
			// this.release = null;
			return PostemToolConstants.PERMISSION_ERROR;
		}
		
		if (gradebook.getId() == null) {
			ArrayList gb = getGradebooks();
			Iterator gi = gb.iterator();
			while (gi.hasNext()) {
				if (((Gradebook) gi.next()).getTitle().equals(
						gradebook.getTitle())) {
					//alert message "duplicate_title"
					return PostemToolConstants.DUPLICATE_TITLE;
				}
			}
		}
		System.out.println();
//		if (currentGradebook.getTitle() == null
//				|| currentGradebook.getTitle().equals("")) {
//			//To stay consistent, remove current messages when adding a new message
//			//so as to only display one error message before returning
//			PostemTool.clearMessages();
//			PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "missing_title",
//					new Object[] {});
//			return "create_gradebook";
//		}
//		else if(currentGradebook.getTitle().trim().length() > TITLE_MAX_LENGTH) {
//			PostemTool.clearMessages();
//			PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "title_too_long",
//					new Object[] { new Integer(currentGradebook.getTitle().trim().length()), new Integer(TITLE_MAX_LENGTH)});
//			return "create_gradebook";
//		}
//		
//		Reference attachment = getAttachmentReference();
//		if (attachment == null){			
//			PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "missing_csv", new Object[] {});
//			return "create_gradebook";
//		}
//		
//		if (!this.delimiter.equals(COMMA_DELIM_STR) && !this.delimiter.equals(TAB_DELIM_STR)) {
//			PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "invalid_delim", new Object[] {});
//			return "create_gradebook";
//		}
//
//		if (attachment != null) {
//			// logger.info("*** Non-Empty CSV!");
//			try {
//				
//				char csv_delim = CSV.COMMA_DELIM;
//				if(this.delimiter.equals(TAB_DELIM_STR)) {
//					csv_delim = CSV.TAB_DELIM;
//				}
//				
//				//Read the data
//				
//				ContentResource cr = contentHostingService.getResource(attachment.getId());
//				//Check the type
//				if (ResourceProperties.TYPE_URL.equalsIgnoreCase(cr.getContentType())) {
//					//Going to need to read from a stream
//					String csvURL = new String(cr.getContent());
//					//Load the URL
//					csv = URLConnectionReader.getText(csvURL); 
//					if (log.isDebugEnabled()) {
//						log.debug(csv);
//					}
//				}
//				else {
//					// check that file is actually a CSV file
//					if (!cr.getContentType().equalsIgnoreCase("text/csv")) {
//						PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "invalid_ext", new Object[] {getAttachmentTitle()});
//						return "create_gradebook";
//					}
//
//					csv = new String(cr.getContent());
//					if (log.isDebugEnabled()) {
//						log.debug(csv);
//					}
//				}
//				CSV grades = new CSV(csv, withHeader, csv_delim);
//				
//				if (withHeader == true) {
//					if (grades.getHeaders() != null) {
//
//						List headingList = grades.getHeaders();
//						for(int col=0; col < headingList.size(); col++) {
//							String heading = (String)headingList.get(col).toString().trim();	
//							// Make sure there are no blank headings
//							if(heading == null || heading.equals("")) {
//								PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR,
//										"blank_headings", new Object[] {});
//								return "create_gradebook";
//							}
//							// Make sure the headings don't exceed max limit
//							if (heading.length() > HEADING_MAX_LENGTH) {
//								PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "heading_too_long", new Object[] {new Integer(HEADING_MAX_LENGTH)});
//								return "create_gradebook";
//							}
//						}
//					}
//				}
//				
//				if (grades.getStudents() != null) {
//				  if(!usernamesValid(grades)) {
//					  return "create_gradebook";
//				  }
//				  
//				  if (hasADuplicateUsername(grades)) {
//					  return "create_gradebook";
//				  }
//				}
//				
//				if (this.newTemplate != null && this.newTemplate.trim().length() > 0) {
//					if(this.newTemplate.trim().length() > TEMPLATE_MAX_LENGTH) {
//						PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "template_too_long",
//								new Object[] { new Integer(this.newTemplate.trim().length()), new Integer(TEMPLATE_MAX_LENGTH)});
//						return "create_gradebook";
//					}
//				}
//				
//				if (withHeader == true) {
//					if (grades.getHeaders() != null) {	
//						PostemTool.populateMessage(FacesMessage.SEVERITY_INFO,
//								"has_headers", new Object[] {});
//					}
//				}
//				if (grades.getStudents() != null) {	
//					PostemTool.populateMessage(FacesMessage.SEVERITY_INFO,
//							"has_students", new Object[] { new Integer(grades.getStudents()
//									.size()) });
//				}
//				if (withHeader == true) {
//					currentGradebook.setHeadings(grades.getHeaders());
//				}
//				List slist = grades.getStudents();
//
//				if (oldGradebook.getId() != null && !this.userPressedBack) {
//					Set oldStudents = currentGradebook.getStudents();
//					oldGradebook.setStudents(oldStudents);
//				}
//
//				currentGradebook.setStudents(new TreeSet());
//				// gradebookManager.saveGradebook(currentGradebook);
//				Iterator si = slist.iterator();
//				while (si.hasNext()) {
//					List ss = (List) si.next();
//					String uname = ((String) ss.remove(0)).trim();
//					// logger.info("[POSTEM] processCreate -- adding student " +
//					// uname);
//					gradebookManager.createStudentGradesInGradebook(uname, ss,
//							currentGradebook);
//					if (currentGradebook.getStudents().size() == 1) {
//						currentGradebook.setFirstUploadedUsername(uname);  //otherwise, the verify screen shows first in ABC order
//					}
//				}
//			} catch (DataFormatException exception) {
//				/*
//				 * TODO: properly subclass exception in order to allow for localized
//				 * messages (add getRowNumber/setRowNumber). Set exception message to be
//				 * key in .properties file
//				 */
//				PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, exception
//						.getMessage(), new Object[] {});
//				return "create_gradebook";
//			} catch (IdUnusedException e) {
//				PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, e
//						.getMessage(), new Object[] {});
//				return "create_gradebook";
//			} catch (TypeException e) {
//				PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, e
//						.getMessage(), new Object[] {});
//				return "create_gradebook";
//			} catch (PermissionException e) {
//				PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, e
//						.getMessage(), new Object[] {});
//				return "create_gradebook";
//			} catch (ServerOverloadException e) {
//				PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, e
//						.getMessage(), new Object[] {});
//				return "create_gradebook";
//			} catch (IOException e) {
//				PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, e
//						.getMessage(), new Object[] {});
//				return "create_gradebook";
//			}
//		} else if (this.csv != null) {
//			// logger.info("**** Non Null Empty CSV!");
//			PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "has_students",
//					new Object[] { new Integer(0) });
//			currentGradebook.setHeadings(new ArrayList());
//			if (oldGradebook.getId() != null) {
//				Set oldStudents = currentGradebook.getStudents();
//				oldGradebook.setStudents(oldStudents);
//			}
//
//			currentGradebook.setStudents(new TreeSet());
//		}
//
//		if (this.newTemplate != null && this.newTemplate.trim().length() > 0) {
//			currentGradebook
//					.setTemplate(gradebookManager.createTemplate(newTemplate.trim()));
//		} else if (this.newTemplate != null) {
//			// logger.info("*** Non Null Empty Template!");
//			currentGradebook.setTemplate(null);
//		}
//
//		/*
//		 * if("No".equals(this.release)) { currentGradebook.setReleased(new
//		 * Boolean(false)); //logger.info("Set to No, " +
//		 * currentGradebook.getReleased()); } else {
//		 * currentGradebook.setReleased(new Boolean(true)); //logger.info("Set to
//		 * Yes, " + currentGradebook.getReleased()); }
//		 */
//
//		// gradebookManager.saveGradebook(currentGradebook);
//		// logger.info(currentGradebook.getId());
//		// currentGradebook = null;
//		if ((this.csv != null && this.csv.trim().length() > 0)
//				|| (this.newTemplate != null && this.newTemplate.trim().length() > 0)) {
//			this.csv = null;
//			this.newTemplate = null;
//			return "verify";
//		}
//
//		Iterator oi = oldGradebook.getStudents().iterator();
//		while (oi.hasNext()) {
//			gradebookManager.deleteStudentGrades((StudentGrades) oi.next());
//		}
//		this.userId = SessionManager.getCurrentSessionUserId();
//		currentGradebook.setLastUpdated(new Timestamp(new Date().getTime()));
//		currentGradebook.setLastUpdater(this.userId);
//		gradebookManager.saveGradebook(currentGradebook);
//
//		this.currentGradebook = null;
//		this.oldGradebook = null;
//		this.withHeader = true;
		// this.gradebooks = null;
		return PostemToolConstants.INDEX_TEMPLATE;
	}

	/*
	 * public void setRelease(String release) { this.release = release; }
	 * 
	 * public String getRelease() { return release; }
	 */
	public ArrayList getGradebooks() {
		if (userId == null) {
			userId = sessionManager.getCurrentSessionUserId();
			
			if (userId != null) {
				try {
					userEid = userDirectoryService.getUserEid(userId);
				} catch (UserNotDefinedException e) {
					log.error("UserNotDefinedException", e);
				}
			}
		}

		Placement placement = ToolManager.getCurrentPlacement();
		String currentSiteId = placement.getContext();

		siteId = currentSiteId;
		try {
			if (checkAccess()) {
				// logger.info("**** Getting by context!");
				gradebooks = new ArrayList(gradebookManager
						.getGradebooksByContext(siteId, sortBy, ascending));
			} else {
				// logger.info("**** Getting RELEASED by context!");
				gradebooks = new ArrayList(gradebookManager
						.getReleasedGradebooksByContext(siteId, sortBy, ascending));
			}
		} catch (Exception e) {
			gradebooks = null;
		}

		return gradebooks;
	}

  
  
  

  

}
