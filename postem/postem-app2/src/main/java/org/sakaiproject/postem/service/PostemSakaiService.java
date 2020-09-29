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
import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.api.app.postem.data.GradebookManager;
import org.sakaiproject.api.app.postem.data.StudentGrades;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.FilePickerHelper;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.content.tool.CopyrightDelegate;
import org.sakaiproject.content.tool.ResourcesAction;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.event.api.SessionState;
import org.sakaiproject.event.cover.NotificationService;
import org.sakaiproject.exception.IdLengthException;
import org.sakaiproject.exception.IdUniquenessException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.OverQuotaException;
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
import org.sakaiproject.util.Validator;
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
	
	private static final int TEMPLATE_MAX_LENGTH = 4000;
	private static final int TITLE_MAX_LENGTH = 255;
	private static final int HEADING_MAX_LENGTH = 500;
	
	private static final String COMMA_DELIM_STR = "comma";
	private static final String TAB_DELIM_STR = "tab";
	
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
		if (gradebook.getTitle() == null
				|| gradebook.getTitle().equals("")) {
			return PostemToolConstants.MISSING_TITLE;
		}
		else if(gradebook.getTitle().trim().length() > TITLE_MAX_LENGTH) {
			return PostemToolConstants.TITLE_TOO_LONG;
		}
		
		Reference attachment = getAttachmentReference();
		if (attachment == null){
			//todo tratar attachment
			return PostemToolConstants.MISSING_CSV;
		}
		
//		if (!this.delimiter.equals(COMMA_DELIM_STR) && !this.delimiter.equals(TAB_DELIM_STR)) {
//			PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, "invalid_delim", new Object[] {});
//			return "create_gradebook";
//		}

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
	
	  public Reference getAttachmentReference ()
	  {
	    ToolSession session = sessionManager.getCurrentToolSession();
	    if (session.getAttribute(FilePickerHelper.FILE_PICKER_CANCEL) == null &&
	        session.getAttribute(FilePickerHelper.FILE_PICKER_ATTACHMENTS) != null)
	    {
	      List refs = (List)session.getAttribute(FilePickerHelper.FILE_PICKER_ATTACHMENTS);
	      Reference ref = null;

	      if (refs.size() == 1)
	      {
	        ref = (Reference) refs.get(0);
          attachment=ref;
	        }
	      }
	    session.removeAttribute(FilePickerHelper.FILE_PICKER_ATTACHMENTS);
	    session.removeAttribute(FilePickerHelper.FILE_PICKER_CANCEL);
	    if(filePickerList != null)
	      filePickerList.clear();

	    return attachment;
	  }
	  
	  public String doDragDropUpload (MultipartFile file, HttpServletRequest request) {
		  
			String uploadFileName=null;
			String resourceId = null;
			String collectionName=null;
			String overwrite = request.getParameter("overwrite");
			SessionState state = getState(request);
			ToolSession toolSession = sessionManager.getCurrentToolSession();
			
			String resourceGroup = toolSession.getAttribute("resources.request.create_wizard_collection_id").toString();
			
			try
			{
				//Upload the received file
				//Test that file has been sent in request 
				org.apache.commons.fileupload.FileItem uploadFile = (org.apache.commons.fileupload.FileItem) request.getAttribute("file");
				
				if(uploadFile != null)
				{
					String contentType = uploadFile.getContentType();
					uploadFileName=uploadFile.getName();
					
					String extension = "";
					String basename = uploadFileName.trim();
					if (uploadFileName.contains(".")) {
						String[] parts = uploadFileName.split("\\.");
						basename = parts[0];
						if (parts.length > 1) {
							extension = parts[parts.length - 1];
						}
						for (int i = 1; i < parts.length - 1; i++) {
							basename += "." + parts[i];
						}
					}

						//Method getUniqueFileName was added to change external name of uploaded resources if they exist already in the collection, just the same way that their internal id.
						//However, that is not the way Resources tool works. Internal id is changed but external name is the same for every copy of the same file.
						//So I disable this method call, though it can be enabled again if desired.
						
						//String resourceName = getUniqueFileName(uploadFileName, resourceGroup);
						resourceId = resourceGroup + uploadFileName;
						try{
							//check if resource exists
							ContentHostingService.getResource(resourceId);
							//if it does and overwrite is true save the latest copy
							if(overwrite != null && overwrite.equals("true")){
								resource = ContentHostingService.editResource(resourceId);
							}
							// if no overwrite then simply create a new resource
							else{
								resource = ContentHostingService.addResource(resourceGroup, Validator.escapeResourceName(basename), Validator.escapeResourceName(extension), ResourcesAction.MAXIMUM_ATTEMPTS_FOR_UNIQUENESS);
							}
						}
						// if new resource then save
						catch(IdUnusedException idUnusedException) {
							log.debug("Adding resource {} in current folder ({})", uploadFileName, resourceGroup);
							resource = ContentHostingService.addResource(resourceGroup, Validator.escapeResourceName(basename), Validator.escapeResourceName(extension), ResourcesAction.MAXIMUM_ATTEMPTS_FOR_UNIQUENESS);
						}


					if (resource != null)
					{
						if (contentType!=null) resource.setContentType(contentType);
						
						ResourcePropertiesEdit resourceProps = resource.getPropertiesEdit();
						resourceProps.addProperty(ResourcePropertiesEdit.PROP_DISPLAY_NAME, uploadFileName);

						CopyrightDelegate copyrightDelegate = new CopyrightDelegate();
						copyrightDelegate.captureCopyright(params);
						copyrightDelegate.setCopyrightOnEntity(resourceProps);

						resource.setContent(uploadFile.getInputStream());
						resource.setContentType(contentType);
						resource.setAvailability(hidden, null, null);
						ContentHostingService.commitResource(resource, NotificationService.NOTI_NONE);
						
						if (collection != null){
							collection.setAvailability(hidden, null, null);
							ContentHostingService.commitCollection(collection);
							log.debug("Collection commited: {}", collection.getId());
						}
					}
					else
					{
						addAlert(state, contentResourceBundle.getFormattedMessage("dragndrop.upload.error",new Object[]{uploadFileName}));
						return;
					}
				}
				else
				{
					addAlert(state, contentResourceBundle.getFormattedMessage("dragndrop.upload.error",new Object[]{uploadFileName}));
					return;
				}
			}
			catch (IdUniquenessException e)
			{
				//addAlert(state,contentResourceBundle.getFormattedMessage("dragndrop.duplicated.error",new Object[]{uploadFileName,ResourcesAction.MAXIMUM_ATTEMPTS_FOR_UNIQUENESS}));
				return e.toString();
			}
			catch (OverQuotaException e) {
				//addAlert(state, rb.getString("alert.over-site-upload-quota"));
				return e.toString();
			}
			catch (ServerOverloadException e) {
				//addAlert(state,contentResourceBundle.getFormattedMessage("dragndrop.overload.error",new Object[]{uploadFileName}));
				return  e.toString();
			}
			catch (IdLengthException e) {
				//addAlert(state, contentResourceBundle.getFormattedMessage("dragndrop.length.error", e.getReference(), e.getLimit()));
				return  e.toString();
			}
			catch (Exception e) {
				//addAlert(state, contentResourceBundle.getFormattedMessage("dragndrop.upload.error",new Object[]{uploadFileName}));
				log.warn("Drag and drop upload failed: {}", e);
				return e.toString();
			}
	  }

  
  
  

  

}
