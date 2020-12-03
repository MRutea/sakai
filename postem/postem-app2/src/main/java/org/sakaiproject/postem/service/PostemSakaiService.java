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
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;
import java.util.zip.DataFormatException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.antivirus.api.VirusFoundException;
import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.api.app.postem.data.GradebookManager;
import org.sakaiproject.api.app.postem.data.StudentGrades;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.authz.api.SecurityAdvisor.SecurityAdvice;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.content.api.ContentResourceFilter;
import org.sakaiproject.content.api.FilePickerHelper;
import org.sakaiproject.content.api.ResourceToolAction;
import org.sakaiproject.content.api.ResourceType;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.event.api.SessionState;
import org.sakaiproject.event.cover.NotificationService;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdLengthException;
import org.sakaiproject.exception.IdUniquenessException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InconsistentException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.postem.constants.PostemToolConstants;

import org.sakaiproject.postem.helpers.Pair;
import org.sakaiproject.postem.helpers.URLConnectionReader;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.Validator;
import org.sakaiproject.postem.helpers.CSV;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.content.api.ContentHostingService;

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
	
	private static final int TITLE_MAX_LENGTH = 255;
	private static final int HEADING_MAX_LENGTH = 500;
	
	private static final String TAB_DELIM_STR = "tab";

	protected static final String FILE_UPLOAD_MAX_SIZE = "20";
	protected static final String STATE_CONTENT_SERVICE = "DbContentService";
	/** The name of the state attribute containing the name of the tool that invoked Resources as attachment helper */
	protected static final String PREFIX = "filepicker.";
	public static final String STATE_ATTACH_TOOL_NAME = PREFIX + "attach_tool_name";
	protected static final String STATE_ATTACHMENT_FILTER = PREFIX + "attachment_filter";
	protected static final String STATE_ADDED_ITEMS = PREFIX + "added_items";
	protected boolean withHeader = true;
	
	/** kernel api **/
	private static SecurityService securityService  = ComponentManager.get(SecurityService.class);
	private static ToolManager toolManager = ComponentManager.get(ToolManager.class);
	
	@Autowired
	private SessionManager sessionManager;
	
	@Autowired
	private UserDirectoryService userDirectoryService;
	
	@Autowired
	private GradebookManager gradebookManager;
	
	@Autowired
	private ContentHostingService contentHostingService;
	
	@Autowired
	private AuthzGroupService authzGroupService;

	public ArrayList getGradebooks(String sortBy, boolean ascending) {
			String userId = sessionManager.getCurrentSessionUserId();
			
			if (userId != null) {
				try {
					userEid = userDirectoryService.getUserEid(userId);
				} catch (UserNotDefinedException e) {
					log.error("UserNotDefinedException", e);
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
		return PostemToolConstants.RESULT_OK;
		
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

  public String doDragDropUpload (MultipartFile file, HttpServletRequest request) {
		 
		ToolSession toolSession = sessionManager.getCurrentToolSession();
		String max_file_size_mb = FILE_UPLOAD_MAX_SIZE;
		long max_bytes = 1024L * 1024L;
		String fileName = "";
		
		try
		{
			max_bytes = Long.parseLong(max_file_size_mb) * 1024L * 1024L;
		}
		catch(Exception e)
		{
			// if unable to parse an integer from the value
			// in the properties file, use 1 MB as a default
			max_file_size_mb = "1";
			max_bytes = 1024L * 1024L;
		}
		
		if(file == null)
		{
			// "The user submitted an empty file
			return "empty_file";
		}
		
		if (file.isEmpty()) {
			return "file_empty";
		}
		else if (file.getResource().getFilename() == null || file.getResource().getFilename().length() == 0)
		{
			return "Please choose the file to attach.";
		}
		else if (file.getResource().getFilename().length()  > TITLE_MAX_LENGTH) 
		{
			return PostemToolConstants.TITLE_TOO_LONG;
		}
		else if (file.getResource().getFilename().length() > 0)
		{
			fileName = FilenameUtils.getName(file.getResource().getFilename());
			InputStream fileContentStream = null;
			try {
				fileContentStream = file.getInputStream();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			// Store contentLength as long for future-proofing, though in many cases this
			// may simply be -1 (unknown), so the length check is of limited use
			long contentLength = file.getSize();
			String contentType = file.getContentType();

			if(contentLength >= max_bytes)
			{
				return "file_too_big";
			}
			else if(fileContentStream != null)
			{
		        SecurityAdvisor advisor = new SecurityAdvisor() {
		            public SecurityAdvice isAllowed(String userId, String function, String reference) {
		                return SecurityAdvice.ALLOWED;
		            }
		        };
		        securityService.pushAdvisor(advisor);
		        try {
					String siteId = toolManager.getCurrentPlacement().getContext();
					String toolName = toolManager.getCurrentPlacement().getTitle();
		            String collection = Entity.SEPARATOR + "attachment" + Entity.SEPARATOR + siteId + Entity.SEPARATOR + toolName + Entity.SEPARATOR;
		            int lastIndexOf = fileName.lastIndexOf("/");
		            if (lastIndexOf != -1 && (fileName.length() > lastIndexOf + 1)) {
		                fileName = fileName.substring(lastIndexOf + 1);
		            }
		            String suffix = "";
		            String finalFileName = "";
		            lastIndexOf = fileName.lastIndexOf(".");
		            if (lastIndexOf != -1 && (fileName.length() > lastIndexOf + 1)) {
		                suffix = fileName.substring(lastIndexOf + 1);
		                finalFileName = fileName.substring(0, lastIndexOf);
		            }
		            try {
		                contentHostingService.checkCollection(collection);
		            } catch (Exception e) {
		                // add this collection
		                ContentCollectionEdit toolEdit = contentHostingService.addCollection(collection);
		                contentHostingService.commitCollection(toolEdit);
		            }
		            if (collection.length() + finalFileName.length() + suffix.length() > TITLE_MAX_LENGTH) 
		    		{
		    			return PostemToolConstants.TITLE_TOO_LONG;
		    		}
		            ContentResourceEdit edit = contentHostingService.addResource(collection, finalFileName, suffix, 99999);
		            edit.setContent(fileContentStream);
		            contentHostingService.commitResource(edit, NotificationService.NOTI_NONE);
		            toolSession.setAttribute("attachmentId", edit.getId());
		        } catch (Exception e) {
		            log.error("Failed to store file.", e);
		            fileName = "";
		            return null;
		        } finally {
		            securityService.popAdvisor(advisor);
		        }
				
			}
			
		}
	  toolSession.setAttribute("file", fileName);
	  return PostemToolConstants.RESULT_OK;
  }
  
  public Gradebook createEmptyGradebook(String creator, String context) {
	    Gradebook gradebook = gradebookManager.createEmptyGradebook(creator, context);
	    return gradebook;
	  }
  
  public String processCreate(Gradebook currentGradebook) {

		try {
			if (!this.checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			this.currentGradebook = null;
			this.csv = null;
			return PostemToolConstants.PERMISSION_ERROR;
		}
		
		ToolSession toolSession = sessionManager.getCurrentToolSession();
		
		if (currentGradebook.getId() == null && currentGradebook.getFileReference() == null) {
			ArrayList gb = getGradebooks();
			Iterator gi = gb.iterator();
			while (gi.hasNext()) {
				if (((Gradebook) gi.next()).getTitle().equals(
						currentGradebook.getTitle())) {
					//alert message "duplicate_title"
					return PostemToolConstants.DUPLICATE_TITLE;
				}
			}
		}
		if (currentGradebook.getTitle() == null
				|| currentGradebook.getTitle().equals("")) {
			return PostemToolConstants.MISSING_TITLE;
		}
		else if(currentGradebook.getTitle().trim().length() > TITLE_MAX_LENGTH) {
			return PostemToolConstants.TITLE_TOO_LONG;
		}
		
		Reference attachment = getAttachmentReference();
		if (attachment == null){
			//todo tratar attachment
			//return PostemToolConstants.MISSING_CSV;
		}

		if (toolSession.getAttribute("attachmentId") != null) {
			try {
				
				char csv_delim = CSV.COMMA_DELIM;
				
				//Read the data from attachment
				String attachmentId = (String) toolSession.getAttribute("attachmentId");
				ContentResource cr = contentHostingService.getResource(attachmentId);
				//Check the type
				if (ResourceProperties.TYPE_URL.equalsIgnoreCase(cr.getContentType())) {
					//Going to need to read from a stream
					String csvURL = new String(cr.getContent());
					//Load the URL
					csv = URLConnectionReader.getText(csvURL); 
					if (log.isDebugEnabled()) {
						log.debug(csv);
					}
				}
				else {
					// check that file is actually a CSV file
					if (!cr.getContentType().equalsIgnoreCase("text/csv")) {
						return "invalid_ext";
					}

					csv = new String(cr.getContent());
					if (log.isDebugEnabled()) {
						log.debug(csv);
					}
				}
				CSV grades = new CSV(csv, withHeader, csv_delim);
				
				if (withHeader == true) {
					if (grades.getHeaders() != null) {

						List headingList = grades.getHeaders();
						for(int col=0; col < headingList.size(); col++) {
							String heading = (String)headingList.get(col).toString().trim();	
							// Make sure there are no blank headings
							if(heading == null || heading.equals("")) {
								return "blank_headings";
							}
							// Make sure the headings don't exceed max limit
							if (heading.length() > HEADING_MAX_LENGTH) {
								return "heading_too_long";
							}
						}
						currentGradebook.setHeadings(headingList);
					}
				}
				
				if (grades.getStudents() != null) {
				  if(!usernamesValid(grades)) {
					  return "create_gradebook";
				  }
				  
				  if (hasADuplicateUsername(grades)) {
					  return "create_gradebook";
				  }
				}
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
				List slist = grades.getStudents();
//
//				if (oldGradebook.getId() != null && !this.userPressedBack) {
//					Set oldStudents = currentGradebook.getStudents();
//					oldGradebook.setStudents(oldStudents);
//				}
//
				currentGradebook.setStudents(new TreeSet());
				Iterator si = slist.iterator();
				while (si.hasNext()) {
					List ss = (List) si.next();
					String uname = ((String) ss.remove(0)).trim();
					gradebookManager.createStudentGradesInGradebook(uname, ss,
							currentGradebook);
					if (currentGradebook.getStudents().size() == 1) {
						currentGradebook.setFirstUploadedUsername(uname);  //otherwise, the verify screen shows first in ABC order
					}
				}
			} catch (Exception exception) {
				/*
				 * TODO: properly subclass exception in order to allow for localized
				 * messages (add getRowNumber/setRowNumber). Set exception message to be
				 * key in .properties file
				 */
//				PostemTool.populateMessage(FacesMessage.SEVERITY_ERROR, exception
//						.getMessage(), new Object[] {});
//				return "create_gradebook";
				exception.printStackTrace();
			} 
		}
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
//		gradebookManager.saveGradebook(currentGradebook);
//		log.debug(currentGradebook.getId().toString());
//		currentGradebook = null;
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
		currentGradebook.setLastUpdated(new Timestamp(new Date().getTime()));
		currentGradebook.setLastUpdater(sessionManager.getCurrentSessionUserId());
		
		if ( null != currentGradebook.getFileReference() && !currentGradebook.getFileReference().equals("")) {
			String resultDelete = processDelete(currentGradebook.getId());//todo resultdelete
		}
		
		gradebookManager.saveGradebook(currentGradebook);

		return PostemToolConstants.INDEX_TEMPLATE;
	}

	public ArrayList getGradebooks() {
			String userId = sessionManager.getCurrentSessionUserId();
			
			if (userId != null) {
				try {
					userEid = userDirectoryService.getUserEid(userId);
				} catch (UserNotDefinedException e) {
					log.error("UserNotDefinedException", e);
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

	/**
	 * Establish a security advisor to allow the "embedded" azg work to occur
	 * with no need for additional security permissions.
	 */
	protected void enableSecurityAdvisor()
	{
	  // put in a security advisor so we can create citationAdmin site without need
	  // of further permissions
	  securityService.pushAdvisor(new SecurityAdvisor() {
	    public SecurityAdvice isAllowed(String userId, String function, String reference)
	    {
	      return SecurityAdvice.ALLOWED;
	    }
	  });
	}
	
    /**
     * remove all security advisors
     */
    protected void disableSecurityAdvisors()
    {
    	// remove all security advisors
    	securityService.popAdvisor();
    }

	private boolean hasADuplicateUsername(CSV studentGrades) {
		List usernameList = studentGrades.getStudentUsernames();
		List duplicatesList = new ArrayList();
		
		while (usernameList.size() > 0) {
			String username = (String)usernameList.get(0);
			usernameList.remove(username);
			if (usernameList.contains(username)
					&& !duplicatesList.contains(username)) {
				duplicatesList.add(username);
			}
		}
		
		if (duplicatesList.size() <= 0) {
			return false;
		}
		
		if (duplicatesList.size() == 1) {
			System.out.println("single_duplicate_username");
		} else {
			System.out.println("mult_duplicate_usernames");
		}
		
		for (int i=0; i < duplicatesList.size(); i++) {
			System.out.println("duplicate_username");
		}
		
			System.out.println("duplicate_username_dir");
		
		return true;
	}
	
	private boolean usernamesValid(CSV studentGrades) {
		boolean usersAreValid = true;
		List blankRows = new ArrayList();
		List invalidUsernames = new ArrayList();
		int row=1;
		
		List siteMembers = getSiteMembers();
		
		List studentList = studentGrades.getStudentUsernames();
		Iterator studentIter = studentList.iterator();
		while (studentIter.hasNext()) {
			row++;
			String usr = (String) studentIter.next();
			
			if (log.isDebugEnabled()) {
				log.debug("usernamesValid : username=" + usr);
				log.debug("usernamesValid : siteMembers" + siteMembers);
			}
			if (usr == null || usr.equals("")) {

				usersAreValid = false;
				blankRows.add(new Integer(row));
			} else if(siteMembers == null || (siteMembers != null && !siteMembers.contains(getUserDefined(usr)))) {
				  usersAreValid = false;
				  invalidUsernames.add(usr);
			}	
		}
		
		if (blankRows.size() == 1) {
			System.out.println("missing_single_username");
			System.out.println("missing_location");
			System.out.println("missing_username_dir");
		} else if (blankRows.size() > 1) {
			System.out.println("missing_mult_usernames");
			for(int i=0; i < blankRows.size(); i++) {
				System.out.println("missing_location");
			}
			System.out.println("missing_username_dir");
		}
		
		if (invalidUsernames.size() == 1) {
			System.out.println("blank");
			System.out.println("single_invalid_username");
			System.out.println("invalid_username");
			System.out.println("single_invalid_username_dir");
		} else if (invalidUsernames.size() > 1) {
			System.out.println("blank");
			System.out.println("mult_invalid_usernames");
			for(int j=0; j < invalidUsernames.size(); j++) {
				System.out.println("invalid_username");
			}	
			System.out.println("mult_invalid_usernames_dir");
		}
	  return usersAreValid;
	}

	private List getSiteMembers() {
		List siteMembers = new ArrayList();
		try	{
			AuthzGroup realm = authzGroupService.getAuthzGroup("/site/" + getCurrentSiteId());
			siteMembers = new ArrayList(realm.getUsers());
		}
		catch (GroupNotDefinedException e) {
			log.error("GroupNotDefinedException:", e);
		}
		
		return siteMembers;
	}

	private String getCurrentSiteId()
	{
		Placement placement = ToolManager.getCurrentPlacement();
		return placement.getContext();
	}

	//Returns getUser and getUserByEid on the input string
	//@return Either the id of the user, or the same string if not defined
	private String getUserDefined(String usr)
	{
		//Set the original user id
		String userId = usr;
		User userinfo;
		try	{
			userinfo = userDirectoryService.getUser(usr);
			userId = userinfo.getId();
			if (log.isDebugEnabled()) {
				log.debug("getUserDefined: username for " + usr + " is " + userId);
			}
			return userId;
		} 
		catch (UserNotDefinedException e) {
			try
			{
				// try with the user eid
				userinfo = userDirectoryService.getUserByEid(usr);
				userId = userinfo.getId();
			}
			catch (UserNotDefinedException ee)
			{
				//This is mostly expected behavior, don't need to notify about it, the UI can handle it
				if (log.isDebugEnabled()) {
					log.debug("getUserDefined: User Not Defined" + userId);
				}
			}
		}
		return userId;
	}

}
