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
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.api.app.postem.data.Gradebook;
import org.sakaiproject.api.app.postem.data.GradebookManager;
import org.sakaiproject.api.app.postem.data.StudentGrades;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.cover.NotificationService;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.postem.constants.PostemToolConstants;

import org.sakaiproject.postem.helpers.Pair;
import org.sakaiproject.postem.helpers.URLConnectionReader;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.User;
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
	protected String newTemplate;
	
	private static final int TITLE_MAX_LENGTH = 255;
	private static final int HEADING_MAX_LENGTH = 500;

	protected static final String FILE_UPLOAD_MAX_SIZE = "20";
	protected static final String STATE_CONTENT_SERVICE = "DbContentService";

	protected boolean withHeader = true;
	char csv_delim = CSV.COMMA_DELIM;
	
	/** kernel api **/
	private static SecurityService securityService  = ComponentManager.get(SecurityService.class);
	private static ToolManager toolManager = ComponentManager.get(ToolManager.class);
	
	private ToolSession toolSession;
	
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
	
	@Autowired
	private MessageSource messageSource;
	
    @Autowired
    private PreferencesService preferencesService;

	public List<Gradebook> getGradebooks(String sortBy, boolean ascending) {
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
		List<Gradebook> gradebooks = new ArrayList<Gradebook>();
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
			if (!checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
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
			return studentMap;
		}
		
		// otherwise, just load what we need for the current user
		currentGradebook = gradebookManager.getGradebookByIdWithHeadings(gradebookId);
		
		return studentMap;
	}
	
	public Pair processGradebookDelete(Long gradebookId) {
		try {
			if (!checkAccess()) {
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
			if (!checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			return PostemToolConstants.RESULT_KO;
		}
		Gradebook currentGradebook = gradebookManager.getGradebookByIdWithHeadingsAndStudents(gradebookId);
		gradebookManager.deleteGradebook(currentGradebook);
		return PostemToolConstants.RESULT_OK;
	}
	
	public Pair processCsvDownload(Long gradebookId) {
		try {
			if (!checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
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
	
  public boolean checkAccess() {
    return SiteService.allowUpdateSite(ToolManager.getCurrentPlacement().getContext());
  }	
  
  public boolean isEditable() {
    if (editable == null) {
      editable = checkAccess();
    }
    return editable;
  }

  public Pair getGradebookById(Long gradebookId) {
	  
      Gradebook currentGradebook = gradebookManager.getGradebookByIdWithHeadingsAndStudents(gradebookId);
      Pair pair = new Pair(null, currentGradebook);
      return pair;
  }
	
  public StudentGrades getStudentByGBAndUsername(Gradebook currentGradebook, String selectedStudent) {
    return gradebookManager.getStudentByGBAndUsername(currentGradebook, selectedStudent);
  }

  public String doDragDropUpload (MultipartFile file, HttpServletRequest request) {
		
		String max_file_size_mb = ServerConfigurationService.getString(PostemToolConstants.SAK_PROP_MAX_UPLOAD_FILE_SIZE);
		long max_bytes = 1024L * 1024L;
		String fileName = "";
		toolSession = sessionManager.getCurrentToolSession();
		
		try
		{
			max_bytes = Long.parseLong(max_file_size_mb) * 1024L * 1024L;
		}
		catch(Exception e)
		{
			max_file_size_mb = "20";
			max_bytes = 1024L * 1024L;
		}
		
		if(file == null)
		{
			//The user submitted an empty file
			return PostemToolConstants.GENERIC_UPLOAD_ERROR;
		}
		
		if (file.isEmpty()) {
			return PostemToolConstants.GENERIC_UPLOAD_ERROR;
		}
		else if (file.getResource().getFilename() == null || file.getResource().getFilename().length() == 0)
		{
			return PostemToolConstants.GENERIC_UPLOAD_ERROR;
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
				return PostemToolConstants.GENERIC_UPLOAD_ERROR;
			}
			
			long contentLength = file.getSize();
			String contentType = file.getContentType();

			if(contentLength >= max_bytes)
			{
				return PostemToolConstants.FILE_TOO_BIG;
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
		                ContentCollectionEdit toolEdit = contentHostingService.addCollection(collection);
		                contentHostingService.commitCollection(toolEdit);
		            }
		            if (collection.length() + finalFileName.length() + suffix.length() > TITLE_MAX_LENGTH) 
		    		{
		    			return PostemToolConstants.NAME_FILE_TOO_LONG;
		    		}
		            ContentResourceEdit edit = contentHostingService.addResource(collection, finalFileName, suffix, 99999);
		            edit.setContent(fileContentStream);
		            contentHostingService.commitResource(edit, NotificationService.NOTI_NONE);
		            toolSession.setAttribute("attachmentId", edit.getId());
		        } catch (Exception e) {
		            log.error("Failed to store file.", e);
		            fileName = "";
		    		toolSession.setAttribute("attachmentId", "");
		            return PostemToolConstants.GENERIC_UPLOAD_ERROR;
		        } finally {
		            securityService.popAdvisor(advisor);
		        }
				
			}
			
		}
	  return PostemToolConstants.RESULT_OK;
  }
  
  public Gradebook createEmptyGradebook(String creator, String context) {
	    Gradebook gradebook = gradebookManager.createEmptyGradebook(creator, context);
	    return gradebook;
	  }
  
  public String processCreate(Gradebook currentGradebook, boolean isGradebookUpdate) {
	  
		toolSession = sessionManager.getCurrentToolSession();

		try {
			if (!this.checkAccess()) {
				throw new PermissionException(sessionManager.getCurrentSessionUserId(),
						"syllabus_access_athz", "");
			}

		} catch (PermissionException e) {
			return PostemToolConstants.PERMISSION_ERROR;
		}
		
		if (null != currentGradebook && null != currentGradebook.getTitle()) {
			ArrayList<Gradebook> gb = getGradebooks();			
			List<Gradebook> result = gb.stream().filter(gradeb -> gradeb.getTitle().equals(currentGradebook.getTitle()))
					.filter(gradeb -> !gradeb.getId().equals(currentGradebook.getId())).collect(Collectors.toList());
			if (result.size()>0) {
					return PostemToolConstants.DUPLICATE_TITLE;
			}
		}
		
		if (currentGradebook.getTitle() == null
				|| currentGradebook.getTitle().equals("")) {
			return PostemToolConstants.MISSING_TITLE;
		}
		else if(currentGradebook.getTitle().trim().length() > TITLE_MAX_LENGTH) {
			return PostemToolConstants.TITLE_TOO_LONG;
		}

		if (toolSession.getAttribute("attachmentId") != null) {
			try {
				
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
						return PostemToolConstants.INVALID_EXT;
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
							if(heading.equals("") && grades.getStudents().size() == 0) {
								return PostemToolConstants.EMPTY_FILE;
							}
							// Make sure there are no blank headings
							if(heading == null || heading.equals("")) {
								return PostemToolConstants.BLANK_HEADINGS;
							}
							// Make sure the headings don't exceed max limit
							if (heading.length() > HEADING_MAX_LENGTH) {
								return PostemToolConstants.HEADING_TOO_LONG;
							}
						}
						currentGradebook.setHeadings(headingList);
					}
				}
				
				if (grades.getStudents() != null) {
					
				    if(grades.getStudents().size() == 0) {
					  return PostemToolConstants.CSV_WITHOUT_STUDENTS;
				    }
					
				    String usernamesValid =  usernamesValid(grades);
				    if(null != usernamesValid) {
				        final Locale locale = StringUtils.isNotBlank(userId) ? preferencesService.getLocale(userId) : Locale.getDefault();
					    return usernamesValid;
				    }
				  
				    if (hasADuplicateUserName(grades)) {
					  return PostemToolConstants.HAS_DUPLICATE_USERNAME;
				    }
				}

				List slist = grades.getStudents();
				currentGradebook.setStudents(new TreeSet());
				Iterator si = slist.iterator();
				while (si.hasNext()) {
					List ss = (List) si.next();
					String uname = ((String) ss.remove(0)).trim();
					gradebookManager.createStudentGradesInGradebook(uname, ss,
							currentGradebook);
					if (currentGradebook.getStudents().size() == 1) {
						currentGradebook.setFirstUploadedUsername(uname);
					}
				}
			} catch (Exception exception) {
				return PostemToolConstants.GENERIC_UPLOAD_ERROR;
			} 
		}

		currentGradebook.setLastUpdated(new Timestamp(new Date().getTime()));
		currentGradebook.setLastUpdater(sessionManager.getCurrentSessionUserId());
		
		if (isGradebookUpdate) {
			String resultDelete = processDelete(currentGradebook.getId());
			if (resultDelete.equals(PostemToolConstants.RESULT_KO)) {
				return PostemToolConstants.CSV_DELETE_FAIL;
			}
		}

		return PostemToolConstants.RESULT_OK;
	}

	public ArrayList<Gradebook> getGradebooks() {
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
				gradebooks = new ArrayList(gradebookManager
						.getGradebooksByContext(siteId, sortBy, ascending));
			} else {
				gradebooks = new ArrayList(gradebookManager
						.getReleasedGradebooksByContext(siteId, sortBy, ascending));
			}
		} catch (Exception e) {
			gradebooks = null;
		}

		return gradebooks;
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
    
	private boolean hasADuplicateUserName(CSV studentGrades) {
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
		
		return true;
	}

	public String getDuplicateUserNames() {
		
		String duplicates = null;
		try {		
			toolSession = sessionManager.getCurrentToolSession();
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
					return PostemToolConstants.INVALID_EXT;
				}
	
				csv = new String(cr.getContent());
				if (log.isDebugEnabled()) {
					log.debug(csv);
				}
			}
	
			CSV grades = new CSV(csv, withHeader, csv_delim);
			List userNameList = grades.getStudentUsernames();
			List duplicatesList = new ArrayList();
			
			while (userNameList.size() > 0) {
				String userName = (String)userNameList.get(0);
				userNameList.remove(userName);
				if (userNameList.contains(userName)
						&& !duplicatesList.contains(userName)) {
					duplicatesList.add(userName);
				}
			}
			
			if (duplicatesList.size() <= 0) {
				return duplicates;
			}
			
			duplicates = (String) duplicatesList.stream().collect(Collectors.joining(", "));
			
	} catch (Exception exception) {
		exception.printStackTrace();
		log.error("getDuplicateUserNamesException:", exception);
	}
		return duplicates;
	}
	
	private String usernamesValid(CSV studentGrades) {
		
		
		String usersAreValid = null;
		List<Integer> blankRows = new ArrayList<Integer>();
		List<String> invalidUsernames = new ArrayList<String>();
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

				usersAreValid = null;
				blankRows.add(new Integer(row));
			} else if(siteMembers == null || (siteMembers != null && !siteMembers.contains(getUserDefined(usr)))) {
				  usersAreValid = null;
				  invalidUsernames.add(usr);
			}	
		}
		
		if (blankRows.size() >= 1) {
			usersAreValid = PostemToolConstants.BLANK_ROWS;
		}
		
		if (invalidUsernames.size() >= 1) {
			usersAreValid = PostemToolConstants.USER_NAME_INVALID;
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
	
	public String processCreateOk(Gradebook currentGradebook) {
		
		try {
			gradebookManager.saveGradebook(currentGradebook);
		} catch (Exception e) {
			e.printStackTrace();
			return PostemToolConstants.RESULT_KO;
		}
		return PostemToolConstants.RESULT_OK;
	}

}
