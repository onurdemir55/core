package com.dotmarketing.portlets.browser.ajax;

import static com.dotmarketing.business.PermissionAPI.PERMISSION_PUBLISH;
import static com.dotmarketing.business.PermissionAPI.PERMISSION_READ;
import static com.dotmarketing.business.PermissionAPI.PERMISSION_WRITE;
import static com.dotmarketing.business.PermissionAPI.PERMISSION_CAN_ADD_CHILDREN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.dotcms.repackage.org.directwebremoting.WebContext;
import com.dotcms.repackage.org.directwebremoting.WebContextFactory;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.beans.Inode;
import com.dotmarketing.beans.WebAsset;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.business.Role;
import com.dotmarketing.business.util.HostNameComparator;
import com.dotmarketing.business.web.HostWebAPI;
import com.dotmarketing.business.web.UserWebAPI;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.cache.StructureCache;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotHibernateException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.exception.WebAssetException;
import com.dotmarketing.factories.InodeFactory;
import com.dotmarketing.factories.PublishFactory;
import com.dotmarketing.factories.WebAssetFactory;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.business.HostAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.model.ContentletVersionInfo;
import com.dotmarketing.portlets.fileassets.business.FileAsset;
import com.dotmarketing.portlets.fileassets.business.FileAssetAPI;
import com.dotmarketing.portlets.files.business.FileAPI;
import com.dotmarketing.portlets.files.model.File;
import com.dotmarketing.portlets.folders.business.FolderAPI;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.portlets.htmlpageasset.model.HTMLPageAsset;
import com.dotmarketing.portlets.htmlpageasset.model.IHTMLPage;
import com.dotmarketing.portlets.htmlpages.factories.HTMLPageFactory;
import com.dotmarketing.portlets.htmlpages.model.HTMLPage;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.links.factories.LinkFactory;
import com.dotmarketing.portlets.links.model.Link;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.portlets.workflows.business.WorkflowAPI;
import com.dotmarketing.portlets.workflows.model.WorkflowAction;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.InodeUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.WebKeys;
import com.dotmarketing.viewtools.BrowserAPI;
import com.liferay.portal.PortalException;
import com.liferay.portal.SystemException;
import com.liferay.portal.language.LanguageUtil;
import com.liferay.portal.model.User;
import com.liferay.portal.struts.ActionException;

/**
 *
 * @author david
 *
 */
public class BrowserAjax {

	private static PermissionAPI permissionAPI = APILocator.getPermissionAPI();
	private UserWebAPI userAPI = WebAPILocator.getUserWebAPI();
	private HostAPI hostAPI = APILocator.getHostAPI();
	private HostWebAPI hostWebAPI = WebAPILocator.getHostWebAPI();
	private FolderAPI folderAPI = APILocator.getFolderAPI();
	private FileAPI fileAPI = APILocator.getFileAPI();
	private ContentletAPI contAPI = APILocator.getContentletAPI();
	private LanguageAPI languageAPI = APILocator.getLanguageAPI();
	private BrowserAPI browserAPI = new BrowserAPI();

	String activeHostId = "";
    String activeFolderInode = "";
    private static String SELECTED_BROWSER_PATH_OBJECT = "SELECTED_BROWSER_PATH_OBJECT";
    List<String> openFolders = new ArrayList<String> ();

    String lastSortBy = "name";
    boolean lastSortDirectionDesc = false;


    /**
	 * @param permissionAPI the permissionAPI to set
	 */
	public static void setPermissionAPI(PermissionAPI permissionAPI) {
		BrowserAjax.permissionAPI = permissionAPI;
	}

    /**
     * This methods is used to load the entire tree by first time.
     * @return The whole folders tree structure.
     * @throws DotDataException
     * @throws DotSecurityException
     */
    public List<Map> getTree(String hostId) throws DotDataException, DotSecurityException {

        WebContext ctx = WebContextFactory.get();
        User usr = getUser(ctx.getHttpServletRequest());
        User systemUser = userAPI.getSystemUser();
        Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(usr.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e1) {
			Logger.error(BrowserAjax.class,e1.getMessage(),e1);
		}

		List<Host> hosts=new ArrayList<Host>();
		if(!UtilMethods.isSet(hostId) || hostId.equals("allHosts")){
			hostId = "allHosts";
			hosts.addAll(hostAPI.findAll(usr, false));
		}
		else{
			hosts.add(hostAPI.find(hostId, usr, false));
        }
        List<Map> retList = new ArrayList<Map>();
         for (Host host : hosts) {

        	//Ignore system host
        	if(host.isSystemHost()||host.isArchived())
        	continue;

        	//Obtain maps from hosts to be returned by ajax
            Map<String,Object> hostMap = (Map<String,Object>)host.getMap();
            if (activeHostId.equalsIgnoreCase(host.getIdentifier())|| hosts.size()==1 )  {
                hostMap.put("open", true);
                List<Map> children = getFoldersTree (host, roles);
                hostMap.put("childrenFolders", children);
                hostMap.put("childrenFoldersCount", children.size());
            } else {
                hostMap.put("open", false);
//                hostMap.put("childrenFoldersCount", getSubFoldersCount(host));
            }

            java.util.List permissions = new ArrayList();
			try {
				permissions = permissionAPI.getPermissionIdsFromRoles(host, roles, usr);
			} catch (DotDataException e) {
				Logger.error(this, "Could not load permissions : ",e);
			}

            hostMap.put("permissions", permissions);
            retList.add(hostMap);

        }

        return retList;
    }

    /**
     * Action called every time a user opens a folder using the + (left hand side)
     * @param hostId
     * @return The subtree structure of folders
     * @throws SystemException
     * @throws PortalException
     * @throws DotSecurityException
     * @throws DotDataException
     */
    public List<Map> openHostTree (String hostId) throws PortalException, SystemException, DotDataException, DotSecurityException {

        WebContext ctx = WebContextFactory.get();
        User usr = getUser(ctx.getHttpServletRequest());
        boolean respectFrontend = !userAPI.isLoggedToBackend(ctx.getHttpServletRequest());

        Host host = hostAPI.find(hostId, usr, respectFrontend);

        if(! UtilMethods.isSet(hostId) || host == null){
        	Host browseHost = hostAPI.find(hostId, APILocator.getUserAPI().getSystemUser(), respectFrontend);
        	if(browseHost != null){
        		Logger.warn(this, "User " + usr.getUserId() + " cannot browse host id " + hostId + " aka "+ browseHost.getHostname());
        	}
        	else{
        		Logger.warn(this, "User " + usr.getUserId() + " cannot browse host id " + hostId );
        	}
        	return new ArrayList<Map>();
        }

        activeHostId = hostId;
        Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(usr.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e) {
			Logger.error(BrowserAjax.class,e.getMessage(),e);
		}

        return getFoldersTree (host, roles);
    }

    /**
     * Action called every time a user opens a folder using the + (left hand side)
     * @param parentInode Parent folder to be opened
     * @return The subtree structure of folders
     * @throws DotDataException
     * @throws DotSecurityException
     * @throws DotHibernateException
     */
    public List<Map> openFolderTree (String parentInode) throws DotHibernateException, DotSecurityException, DotDataException {
        WebContext ctx = WebContextFactory.get();
        User usr = getUser(ctx.getHttpServletRequest());
        Role[] roles = new Role[]{};
        if(usr != null){
			try {
				roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(usr.getUserId()).toArray(new Role[0]);
			}catch (NullPointerException e) {
				Logger.debug(this, "array was null");
			} catch (DotDataException e) {
				Logger.error(BrowserAjax.class,e.getMessage(),e);
			}
        }


        Folder f = (Folder) APILocator.getFolderAPI().find(parentInode, usr, false);
        openFolders.add(parentInode);
        return getFoldersTree (f, roles);
    }

    /**
     * Action called everytime a user closes a folder using the - (left hand side)
     * @param parentInode Parent folder to be opened
     * @return The subtree structure of folders
     */
    public void closeFolderTree (String parentInode) {
        openFolders.remove(parentInode);
    }


    @SuppressWarnings("unchecked")
	public List<Map<String, Object>> openFolderContent (String parentInode, String sortBy, boolean showArchived, long languageId) throws DotHibernateException, DotSecurityException, DotDataException {

        activeFolderInode = parentInode;
        this.lastSortBy = sortBy;

    	if (sortBy != null && UtilMethods.isSet(sortBy)) {
    		if (sortBy.equals(lastSortBy)) {
    			this.lastSortDirectionDesc = !this.lastSortDirectionDesc;
    		}
    		this.lastSortBy = sortBy;
    	}

        Map<String, Object> resultsMap = getFolderContent(parentInode, 0, -1, "", null, null, showArchived, false, false, this.lastSortBy, this.lastSortDirectionDesc, languageId);
        return (List<Map<String, Object>>) resultsMap.get("list");
    }

	public Map<String, Object> getFolderContent (String folderId, int offset, int maxResults, String filter, List<String> mimeTypes,
			List<String> extensions, boolean showArchived, boolean noFolders, boolean onlyFiles, String sortBy, boolean sortByDesc, boolean excludeLinks, long languageId) throws DotHibernateException, DotSecurityException, DotDataException {

		WebContext ctx = WebContextFactory.get();
		HttpServletRequest req = ctx.getHttpServletRequest();
		User usr = getUser(req);
		HttpSession session = ctx.getSession();
		Map<String, Object> selectedBrowserPathObject = new HashMap<String, Object>();
		if(UtilMethods.isSet(folderId)){
			selectedBrowserPathObject.put("path", getSelectedBrowserPathArray(folderId));
			try {
				selectedBrowserPathObject.put("currentFolder", getFolderMap(folderId));
			} catch (Exception e) {}
			session.setAttribute(SELECTED_BROWSER_PATH_OBJECT, selectedBrowserPathObject);
		}

		req.getSession().setAttribute(WebKeys.LANGUAGE_SEARCHED, String.valueOf(languageId));

		return browserAPI.getFolderContent(usr, folderId, offset, maxResults, filter, mimeTypes, extensions, showArchived, noFolders, onlyFiles, sortBy, sortByDesc, excludeLinks, languageId);
	}

	private String[] getSelectedBrowserPathArray(String folderId) {
		List<String> selectedPath = new ArrayList<String>();
		Folder parentFolder = new Folder();
		String[] pathArray = new String[]{"root"};
		try{
			User systemUser = APILocator.getUserAPI().getSystemUser();
			selectedPath.add(folderId);
			String hostId = folderAPI.find(folderId, systemUser, false).getHostId();
			while(parentFolder != null){
				parentFolder = folderAPI.findParentFolder(folderAPI.find(folderId, systemUser, false), systemUser, false);
				if(parentFolder != null){
					selectedPath.add(parentFolder.getInode());
					folderId = parentFolder.getInode();
				}else{
					break;
				}
			}
			pathArray = new String[selectedPath.size()+2];
			int index = 0;
			for(int i = selectedPath.size()+1; i > 1 ; i--){
				pathArray[i] = selectedPath.get(index);
				index++;
			}
			pathArray[0] = "root";
			pathArray[1] = hostId;
		}catch(Exception e){}
		return pathArray;
	}

	public Map<String, Object> getFolderContent (String folderId, int offset, int maxResults, String filter, List<String> mimeTypes,
			List<String> extensions, boolean showArchived, boolean noFolders, boolean onlyFiles, String sortBy, boolean sortByDesc, long languageId) throws DotHibernateException, DotSecurityException, DotDataException {

		WebContext ctx = WebContextFactory.get();
		HttpServletRequest req = ctx.getHttpServletRequest();
		User usr = getUser(req);
		
		req.getSession().setAttribute(WebKeys.LANGUAGE_SEARCHED, String.valueOf(languageId));

		return browserAPI.getFolderContent(usr, folderId, offset, maxResults, filter, mimeTypes, extensions, showArchived, noFolders, onlyFiles, sortBy, sortByDesc, languageId);
	}

	/**
	 * Retrieves the list of contents under the specified folder. This specific
	 * implementation will only have one identifier per entry. This means that,
	 * for elements such as the new content pages, the list will not contain all
	 * the entries for all the available languages, but only the page in the
	 * default language, or the page in the next available language (one single 
	 * entry per identifier).
	 * 
	 * @param folderId
	 *            - The identifier of the folder whose contents will be
	 *            retrieved.
	 * @param offset
	 *            - The result offset.
	 * @param maxResults
	 *            - The maximum amount of results to return.
	 * @param filter
	 *            - The parameter used to filter the results.
	 * @param mimeTypes
	 *            - The allowed MIME types.
	 * @param extensions
	 *            - The allowed extensions.
	 * @param showArchived
	 *            - If <code>true</code>, retrieve archived elements too.
	 *            Otherwise, set to <code>false</code>.
	 * @param noFolders
	 *            - If <code>true</code>, retrieve everything except for
	 *            folders. Otherwise, set to <code>false</code>.
	 * @param onlyFiles
	 *            - If <code>true</code>, retrieve only file elements.
	 *            Otherwise, set to <code>false</code>.
	 * @param sortBy
	 *            - The sorting parameter.
	 * @param sortByDesc
	 * @param excludeLinks
	 *            - If <code>true</code>, include Links as part of the results.
	 *            Otherwise, set to <code>false</code>.
	 * @return a {@link Map} containing the information of the elements under
	 *         the given folder.
	 * @throws DotHibernateException
	 *             An error occurred during a Hibernate transaction.
	 * @throws DotSecurityException
	 *             The current user does not have permission to perform this
	 *             action.
	 * @throws DotDataException
	 *             An error occurred when interacting with the database.
	 */
	public Map<String, Object> getFolderContent(String folderId, int offset,
			int maxResults, String filter, List<String> mimeTypes,
			List<String> extensions, boolean showArchived, boolean noFolders,
			boolean onlyFiles, String sortBy, boolean sortByDesc,
			boolean excludeLinks) throws DotHibernateException,
			DotSecurityException, DotDataException {
		WebContext ctx = WebContextFactory.get();
		HttpServletRequest req = ctx.getHttpServletRequest();
		User usr = getUser(req);
		//Language selectedLang = APILocator.getLanguageAPI().getLanguage(languageId);
		//req.getSession().setAttribute(WebKeys.LANGUAGE_SEARCHED, selectedLang);
		long getAllLanguages = 0;
		Map<String, Object> results = browserAPI.getFolderContent(usr,
				folderId, offset, maxResults, filter, mimeTypes, extensions,
				showArchived, noFolders, onlyFiles, sortBy, sortByDesc,
				excludeLinks, getAllLanguages);
		pageListCleanup((List<Map<String, Object>>) results.get("list"));
		return results;
	}

	/**
	 * The list of content pages under a folder contains all the legacy pages
	 * and the new content pages. The latter might include the page identifier
	 * several times, representing all the available languages for a single
	 * page.
	 * <p>
	 * This method takes that list and <i>leaves only one identifier per
	 * page</i>. This unique record represents either the page with the default
	 * language ID, or the page with the next language ID in the list of system
	 * languages.
	 * </p>
	 * 
	 * @param results
	 *            - The full list of pages under a given path/directory.
	 */
	private void pageListCleanup(List<Map<String, Object>> results) {
		Map<String, Integer> pageLangCounter = new HashMap<String, Integer>();
		// Examine only the pages with more than 1 assigned language
		for (Map<String, Object> pageInfo : results) {
			if ((boolean) pageInfo.get("isContentlet")) {
				String ident = (String) pageInfo.get("identifier");
				if (pageLangCounter.containsKey(ident)) {
					int counter = pageLangCounter.get(ident);
					pageLangCounter.put(ident, counter + 1);
				} else {
					pageLangCounter.put(ident, 1);
				}
			}
		}
		Set<String> identifierSet = pageLangCounter.keySet();
		for (String identifier : identifierSet) {
			int counter = pageLangCounter.get(identifier);
			if (counter > 1) {
				long defaultLang = this.languageAPI.getDefaultLanguage()
						.getId();
				// Remove all languages except the default one
				boolean isDeleted = removeAdditionalLanguages(identifier,
						results, defaultLang);
				if (!isDeleted) {
					// Otherwise, leave only the next available language
					List<Language> languages = this.languageAPI.getLanguages();
					for (Language language : languages) {
						if (language.getId() != defaultLang) {
							isDeleted = removeAdditionalLanguages(identifier,
									results, language.getId());
							if (isDeleted) {
								break;
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Removes all other pages from the given list that ARE NOT associated to
	 * the specified language ID. In the end, the list will contain one page per
	 * identifier with either the default language ID or the next available
	 * language.
	 * 
	 * @param identifier
	 *            - The identifier of the page to clean up in the list.
	 * @param resultList
	 *            - The list of all pages that will be displayed.
	 * @param languageId
	 *            - The language ID of the page that will remain in the list.
	 * @return If <code>true</code>, the identifier with the specified language
	 *         ID was successfully cleaned up. If <code>false</code>, the
	 *         identifier is not associated to the specified language ID and was
	 *         not removed from the list.
	 */
	private boolean removeAdditionalLanguages(String identifier,
			List<Map<String, Object>> resultList, long languageId) {
		boolean removeOtherLangs = false;
		for (int i = 0; i < resultList.size(); i++) {
			Map<String, Object> pageInfo = resultList.get(i);
			if ((boolean) pageInfo.get("isContentlet")) {
				String ident = (String) pageInfo.get("identifier");
				if (identifier.equals(ident)) {
					long langId = (long) pageInfo.get("languageId");
					// If specified language is found, remove all others
					if (languageId == langId) {
						removeOtherLangs = true;
						break;
					}
				}
			}
		}
		if (removeOtherLangs) {
			removeLangOtherThan(resultList, identifier, languageId);
		}
		return removeOtherLangs;
	}

	/**
	 * Removes all the pages from the list that are not associated to the
	 * specified language. In the end, the list will contain one page per
	 * identifier.
	 * 
	 * @param resultList
	 *            - The list of pages that will be displayed.
	 * @param identifier
	 *            - The identifier for the page to lookup in the list.
	 * @param languageId
	 *            - The language ID that <b>MUST REMAIN</b> in the list.
	 */
	private void removeLangOtherThan(List<Map<String, Object>> resultList,
			String identifier, long languageId) {
		List<Integer> itemsToRemove = new ArrayList<Integer>();
		for (int i = 0; i < resultList.size(); i++) {
			Map<String, Object> pageInfo = resultList.get(i);
			if ((boolean) pageInfo.get("isContentlet")) {
				String ident = (String) pageInfo.get("identifier");
				if (identifier.equals(ident)) {
					long langId = (long) pageInfo.get("languageId");
					if (languageId != langId) {
						itemsToRemove.add(i);
					}
				}
			}
		}
		int deletionCounter = 0;
		for (int index : itemsToRemove) {
			// Adjust index based on previous deletions
			int indexAfterDeletion = index - deletionCounter;
			resultList.remove(indexAfterDeletion);
			deletionCounter++;
		}
	}

	public void saveFileAction(String selectedItem,String wfActionAssign,String wfActionId,String wfActionComments, String wfConId, String wfPublishDate,
			String wfPublishTime, String wfExpireDate, String wfExpireTime, String wfNeverExpire, String whereToSend, String forcePush) throws  DotSecurityException, ServletException{
		WebContext ctx = WebContextFactory.get();
        User usr = getUser(ctx.getHttpServletRequest());
		Contentlet c = null;
		WorkflowAPI wapi = APILocator.getWorkflowAPI();
		try {
			WorkflowAction action = wapi.findAction(wfActionId, usr);
			if (action == null) {
				throw new ServletException("No such workflow action");
			}
			c = APILocator.getContentletAPI().find(wfConId, usr, false);
			c.setStringProperty("wfActionId", action.getId());
			c.setStringProperty("wfActionComments", wfActionComments);
			c.setStringProperty("wfActionAssign", wfActionAssign);

			c.setStringProperty("wfPublishDate", wfPublishDate);
			c.setStringProperty("wfPublishTime", wfPublishTime);
			c.setStringProperty("wfExpireDate", wfExpireDate);
			c.setStringProperty("wfExpireTime", wfExpireTime);
			c.setStringProperty("wfNeverExpire", wfNeverExpire);
			c.setStringProperty("whereToSend", whereToSend);
			c.setStringProperty("forcePush", forcePush);

			wapi.fireWorkflowNoCheckin(c, usr);

		} catch (Exception e) {
			Logger.error(BrowserAjax.class, e.getMessage(), e);
			throw new ServletException(e.getMessage());
		}
	}

	public Map<String, Object> getFileInfo(String fileId) throws DotDataException, DotSecurityException, PortalException, SystemException {
        WebContext ctx = WebContextFactory.get();
        HttpServletRequest req = ctx.getHttpServletRequest();
        ServletContext servletContext = ctx.getServletContext();
        User user = userAPI.getLoggedInUser(req);
        boolean respectFrontendRoles = userAPI.isLoggedToFrontend(req);

        Identifier ident = APILocator.getIdentifierAPI().find(fileId);

		if(ident!=null && InodeUtils.isSet(ident.getId()) && ident.getAssetType().equals("file_asset")) {
			File file = fileAPI.getWorkingFileById(fileId, user, respectFrontendRoles);
			String mimeType = servletContext.getMimeType(file.getFileName().toLowerCase());
			Map<String, Object> fileMap = file.getMap();
			fileMap.put("mimeType", mimeType);
			fileMap.put("path", file.getPath());
			return fileMap;
		}

		if(ident!=null && InodeUtils.isSet(ident.getId()) && ident.getAssetType().equals("htmlpage")) {
			Map<String, Object> pageMap = APILocator.getHTMLPageAPI().loadWorkingPageById(fileId, user, respectFrontendRoles).getMap();
			pageMap.put("mimeType", "application/dotpage");
			pageMap.put("pageURI", ident.getURI());
			return pageMap;
		}

		if(ident!=null && InodeUtils.isSet(ident.getId()) && ident.getAssetType().equals("contentlet")) {
		    ContentletVersionInfo vinfo=APILocator.getVersionableAPI().getContentletVersionInfo(ident.getId(), APILocator.getLanguageAPI().getDefaultLanguage().getId());
		    boolean live = respectFrontendRoles || vinfo.getLiveInode()!=null;
			Contentlet cont = contAPI.findContentletByIdentifier(ident.getId(),live, APILocator.getLanguageAPI().getDefaultLanguage().getId() , user, respectFrontendRoles);
			if(cont.getStructure().getStructureType()==Structure.STRUCTURE_TYPE_FILEASSET) {
    			FileAsset fileAsset = APILocator.getFileAssetAPI().fromContentlet(cont);
    			java.io.File file = fileAsset.getFileAsset();
    			String mimeType = servletContext.getMimeType(file.getName().toLowerCase());
    			Map<String, Object> fileMap = fileAsset.getMap();
    			fileMap.put("mimeType", mimeType);
    			fileMap.put("path", fileAsset.getPath());
    			fileMap.put("type", "contentlet");
    			return fileMap;
			}
			else if(cont.getStructure().getStructureType()==Structure.STRUCTURE_TYPE_HTMLPAGE) {
			    HTMLPageAsset page = APILocator.getHTMLPageAssetAPI().fromContentlet(cont);
			    Map<String, Object> pageMap = page.getMap();
			    pageMap.put("mimeType", "application/dotpage");
	            pageMap.put("pageURI", ident.getURI());
	            return pageMap;
			}
		}

		return null;
	}

    @SuppressWarnings("unchecked")
	private List<Map> getFoldersTree (Host host, Role[] roles) throws DotStateException, DotDataException, DotSecurityException {
        FolderAPI folderAPI = APILocator.getFolderAPI();
        List<Folder> children = new ArrayList<Folder>();
		try {
			children = folderAPI.findSubFolders(host,userAPI.getSystemUser(),false);
		} catch (Exception e) {
			Logger.error(this, "Could not load folders : ",e);
		}
        return getFoldersTree(host.getIdentifier(), children, roles);
    }

    @SuppressWarnings("unchecked")
	private List<Map> getFoldersTree (Folder parent, Role[] roles) throws DotStateException, DotDataException, DotSecurityException {
        FolderAPI folderAPI = APILocator.getFolderAPI();
        List<Folder> children = new ArrayList<Folder>();
		try {
			children = folderAPI.findSubFolders(parent,userAPI.getSystemUser(),false);
		} catch (Exception e) {
			Logger.error(this, "Could not load folders : ",e);
		}
        return getFoldersTree(parent.getInode(), children, roles);
    }

	private List<Map> getFoldersTree (String parentInode, List<Folder> children, Role[] roles) throws DotStateException, DotDataException, DotSecurityException {

        WebContext ctx = WebContextFactory.get();
        User usr = getUser(ctx.getHttpServletRequest());
        ArrayList<Map> folders = new ArrayList<Map> ();

        for (Folder f : children) {
        	Map<String, Object> folderMap = f.getMap();
        	if (openFolders.contains(f.getInode())) {
        		List<Map> childrenMaps = getFoldersTree (f, roles);
        		folderMap.put("open", true);
        		folderMap.put("childrenFolders", childrenMaps);
        	} else {
        		folderMap.put("open", false);
        	}
        	if(f.getInode().equalsIgnoreCase(activeFolderInode))
        		folderMap.put("selected", true);
        	else
        		folderMap.put("selected", false);
        	folderMap.put("parent", parentInode);

        	List permissions = new ArrayList();
        	try {
        		permissions = permissionAPI.getPermissionIdsFromRoles(f, roles, usr);
        	} catch (DotDataException e) {
        		Logger.error(this, "Could not load permissions : ",e);
        	}

        	folderMap.put("permissions", permissions);

        	folders.add(folderMap);

        }

        	return folders;
    }

    public Map<String, Object> renameFolder (String inode, String newName) throws DotDataException, DotSecurityException {
    	WebContext ctx = WebContextFactory.get();
        User usr = getUser(ctx.getHttpServletRequest());
    	HashMap<String, Object> result = new HashMap<String, Object> ();
    	Folder folder = APILocator.getFolderAPI().find(inode, usr, false);
    	result.put("lastName", folder.getName());
    	result.put("extension", "");
    	result.put("newName", newName);
    	result.put("inode", folder.getInode());
    	result.put("assetType", "folder");
    	try {
			if (folderAPI.renameFolder(folder, newName,usr,false)) {
				result.put("result", 0);
			} else {
				result.put("result", 1);
				result.put("errorReason", "There is another folder that has the same name");
			}
		} catch (Exception e) {
			Logger.error(this, "Problem occured in the method  renameFolder: ",e);
		}
    	return result;
    }

    /**
     * Copies a given inode folder/host reference into another given folder
     *
     * @param inode     folder inode
     * @param newFolder This could be the inode of a folder or a host
     * @return Confirmation message
     * @throws Exception
     */
    public boolean copyFolder ( String inode, String newFolder ) throws Exception {

        HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser( req );

        UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
        HostAPI hostAPI = APILocator.getHostAPI();

        //Searching for the folder to copy
        Folder folder = APILocator.getFolderAPI().find( inode, user, false );

        if ( !folderAPI.exists( newFolder ) ) {

            Host parentHost = hostAPI.find( newFolder, user, !userWebAPI.isLoggedToBackend( req ) );

            if ( !permissionAPI.doesUserHavePermission( folder, PERMISSION_WRITE, user ) || !permissionAPI.doesUserHavePermission( parentHost, PERMISSION_WRITE, user ) ) {
                throw new DotRuntimeException( "The user doesn't have the required permissions." );
            }

            folderAPI.copy( folder, parentHost, user, false );
            refreshIndex(null, null, user, parentHost, folder );
        } else {

            Folder parentFolder = APILocator.getFolderAPI().find( newFolder, user, false );

            if ( !permissionAPI.doesUserHavePermission( folder, PermissionAPI.PERMISSION_WRITE, user ) || !permissionAPI.doesUserHavePermission( parentFolder, PERMISSION_WRITE, user ) ) {
                throw new DotRuntimeException( "The user doesn't have the required permissions." );
            }

            if ( parentFolder.getInode().equalsIgnoreCase( folder.getInode() ) ) {
                //Trying to move a folder over itself
                return false;
            }
            if ( folderAPI.isChildFolder( parentFolder, folder ) ) {
                //Trying to move a folder over one of its children
                return false;
            }

            folderAPI.copy( folder, parentFolder, user, false );
            refreshIndex(null, parentFolder, user, null, folder );
        }

        return true;
    }

    /**
     * Moves a given inode folder/host reference into another given folder
     *
     * @param inode     folder inode
     * @param newFolder This could be the inode of a folder or a host
     * @return Confirmation message
     * @throws Exception
     */
    public String moveFolder ( String inode, String newFolder ) throws Exception {

        HibernateUtil.startTransaction();

        Locale requestLocale = WebContextFactory.get().getHttpServletRequest().getLocale();
        String successString = UtilMethods.escapeSingleQuotes(LanguageUtil.get(requestLocale, "Folder-moved"));
        String errorString = UtilMethods.escapeSingleQuotes(LanguageUtil.get(requestLocale, "Failed-to-move-another-folder-with-the-same-name-already-exists-in-the-destination"));

        try {
            HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
            User user = getUser( req );

            boolean respectFrontendRoles = !userAPI.isLoggedToBackend( req );

            //Searching for the folder to move
            Folder folder = APILocator.getFolderAPI().find( inode, user, false );

            if ( !folderAPI.exists( newFolder ) ) {

                Host parentHost = hostAPI.find( newFolder, user, respectFrontendRoles );

                if ( !permissionAPI.doesUserHavePermission( folder, PERMISSION_WRITE, user ) || !permissionAPI.doesUserHavePermission( parentHost, PERMISSION_WRITE, user ) ) {
                    throw new DotRuntimeException( "The user doesn't have the required permissions." );
                }

                if ( !folderAPI.move( folder, parentHost, user, respectFrontendRoles ) ) {
                    //A folder with the same name already exists on the destination
                    return errorString;
                }
                refreshIndex(null, null, user, parentHost, folder );
            } else {

                Folder parentFolder = APILocator.getFolderAPI().find( newFolder, user, false );

                if ( !permissionAPI.doesUserHavePermission( folder, PERMISSION_WRITE, user ) || !permissionAPI.doesUserHavePermission( parentFolder, PERMISSION_WRITE, user ) ) {
                    throw new DotRuntimeException( "The user doesn't have the required permissions." );
                }

                if ( parentFolder.getInode().equalsIgnoreCase( folder.getInode() ) ) {
                    //Trying to move a folder over itself
                    return errorString;
                }
                if ( folderAPI.isChildFolder( parentFolder, folder ) ) {
                    //Trying to move a folder over one of its children
                    return errorString;
                }

                if ( !folderAPI.move( folder, parentFolder, user, respectFrontendRoles ) ) {
                    //A folder with the same name already exists on the destination
                    return errorString;
                }

                refreshIndex(null, parentFolder, user, null, folder );
                APILocator.getPermissionAPI().resetPermissionReferences(folder);
            }
        } catch ( Exception e ) {
            HibernateUtil.rollbackTransaction();
            return e.getLocalizedMessage();
        } finally {
            HibernateUtil.commitTransaction();
        }

        return successString;
    }

    public Map<String, Object> renameFile (String inode, String newName) throws Exception {

    	HashMap<String, Object> result = new HashMap<String, Object> ();

    	HibernateUtil.startTransaction();

    	try {

    		HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
    		User user = null;
    		try {
    			user = com.liferay.portal.util.PortalUtil.getUser(req);
    		} catch (Exception e) {
    			Logger.error(this, "Error trying to obtain the current liferay user from the request.", e);
    			throw new DotRuntimeException ("Error trying to obtain the current liferay user from the request.");
    		}

    		Identifier id  = APILocator.getIdentifierAPI().findFromInode(inode);
    		if(id!=null && id.getAssetType().equals("contentlet")){
    			Contentlet cont  = APILocator.getContentletAPI().find(inode, user, false);
    			String lName = (String) cont.get(FileAssetAPI.FILE_NAME_FIELD);
    			result.put("lastName", lName.substring(0, lName.lastIndexOf(".")));
    			result.put("extension", UtilMethods.getFileExtension(cont.getStringProperty(FileAssetAPI.FILE_NAME_FIELD)));
    			result.put("newName", newName);
    			result.put("inode", inode);
    			if(!cont.isLocked()){
    				try{
    					if(APILocator.getFileAssetAPI().renameFile(cont, newName, user, false)){
    						result.put("result", 0);
    					}else{
    						result.put("result", 1);
    						result.put("errorReason", "Another file with the same name already exists on this folder");
    					}
    				}catch(Exception e){
    					result.put("result", 1);
    					result.put("errorReason", e.getLocalizedMessage());
    				}
    			}else{
    				result.put("result", 1);
    				result.put("errorReason", "The file is locked");
    			}
    		}else{
    			File file = (File) InodeFactory.getInode(inode, File.class);
    			result.put("lastName", file.getNameOnly());
    			result.put("extension", file.getExtension());
    			result.put("newName", newName);
    			result.put("inode", inode);
    			if (APILocator.getFileAPI().renameFile(file, newName, user, false)) {
    				result.put("result", 0);
    			} else {
    				result.put("result", 1);
    				if (file.isLocked())
    					result.put("errorReason", "The file is locked");
    				else
    					result.put("errorReason", "Another file with the same name already exists on this folder");
    			}
    		}

    	} catch ( Exception e ) {
    		HibernateUtil.rollbackTransaction();
    	} finally {
    		HibernateUtil.commitTransaction();
    	}

    	return result;

    }

    /**
     * Copies a given inode reference to a given folder
     *
     * @param inode     Contentlet inode
     * @param newFolder This could be the inode of a folder or a host
     * @return Confirmation message
     * @throws Exception
     */
    public String copyFile ( String inode, String newFolder ) throws Exception {
		try{
			HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
			User user = getUser( req );

			//Contentlet file identifier
			Identifier id = APILocator.getIdentifierAPI().findFromInode( inode );

			// gets folder parent
			Folder parent = null;
			try {
				parent = APILocator.getFolderAPI().find( newFolder, user, false );
			} catch ( Exception ignored ) {
				//Probably what we have here is a host
			}

			Host host = null;
			if ( parent == null ) {//If we didn't find a parent folder lets verify if this is a host
				host = APILocator.getHostAPI().find( newFolder, user, false );
			}

			// Checking permissions
			String permissionsError = "File-failed-to-copy-check-you-have-the-required-permissions";
			if ( !permissionAPI.doesUserHavePermission( id, PERMISSION_WRITE, user ) ) {
				return permissionsError;
			} else if ( parent != null && !permissionAPI.doesUserHavePermission( parent, PERMISSION_WRITE, user ) ) {
				return permissionsError;
			} else if ( host != null && !permissionAPI.doesUserHavePermission( host, PERMISSION_WRITE, user ) ) {
				return permissionsError;
			}

			if ( id != null && id.getAssetType().equals( "contentlet" ) ) {

				//Getting the contentlet file
				Contentlet cont = APILocator.getContentletAPI().find( inode, user, false );

				if ( parent != null ) {

					FileAsset fileAsset = APILocator.getFileAssetAPI().fromContentlet( cont );
					if ( UtilMethods.isSet( fileAsset.getFileName() ) && !folderAPI.matchFilter( parent, fileAsset.getFileName() ) ) {
						return "message.file_asset.error.filename.filters";
					}
				}

				if ( parent != null ) {
					APILocator.getContentletAPI().copyContentlet( cont, parent, user, false );
				} else {
					APILocator.getContentletAPI().copyContentlet( cont, host, user, false );
				}

				// issues/1788
				// issues/1967

				Folder srcFolder = APILocator.getFolderAPI().find(cont.getFolder(),user,false);
				refreshIndex(null, parent, user, host, srcFolder );


				return "File-copied";
			}

            File file = (File) InodeFactory.getInode( inode, File.class );
            // CHECK THE FOLDER PATTERN		//DOTCMS-6017
            if ( UtilMethods.isSet( file.getFileName() ) && (parent != null && !folderAPI.matchFilter( parent, file.getFileName() )) ) {
                return "message.file_asset.error.filename.filters";
            }

            // Checking permissions
            if ( !permissionAPI.doesUserHavePermission( file, PERMISSION_WRITE, user ) ) {
                return "File-failed-to-copy-check-you-have-the-required-permissions";
            }

            if ( parent != null ) {
                APILocator.getFileAPI().copyFile( file, parent, user, false );
            } else {
                APILocator.getFileAPI().copyFile( file, host, user, false );
            }
            return "File-copied";
		}
		catch(Exception ex) {
		    Logger.error(this, "Error trying to copy the file to folder.", ex);
		}
        return "";
    }

    /**
     * Moves a given inode reference to a given folder
     *
     * @param inode  Contentlet inode
     * @param folder This could be the inode of a folder or a host
     * @return true if success, false otherwise
     * @throws Exception
     */
    public boolean moveFile ( String inode, String folder ) throws Exception {

        HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser( req );

        //Contentlet file identifier
        Identifier id = APILocator.getIdentifierAPI().findFromInode( inode );

        // gets folder parent
        Folder parent = null;
        try {
            parent = APILocator.getFolderAPI().find( folder, user, false );
        } catch ( Exception ignored ) {
            //Probably what we have here is a host
        }

        Host host = null;
        if ( parent == null ) {//If we didn't find a parent folder lets verify if this is a host
            host = APILocator.getHostAPI().find( folder, user, false );
        }

        // Checking permissions
        if ( !permissionAPI.doesUserHavePermission( id, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( "The user doesn't have the required permissions." );
        } else if ( parent != null && !permissionAPI.doesUserHavePermission( parent, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( "The user doesn't have the required permissions." );
        } else if ( host != null && !permissionAPI.doesUserHavePermission( host, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( "The user doesn't have the required permissions." );
        }

        if ( id != null && id.getAssetType().equals( "contentlet" ) ) {

            //Getting the contentlet file
            Contentlet contentlet = APILocator.getContentletAPI().find( inode, user, false );
            Folder srcFolder = APILocator.getFolderAPI().find(contentlet.getFolder(),user,false);

            if(contentlet.getFolder().equals("SYSTEM_FOLDER")) {
            	refreshIndex(null, null, user, APILocator.getHostAPI().find(contentlet.getHost(), user, false), srcFolder );
            } else {
            	refreshIndex(null, parent, user, host, srcFolder );
            }

            if ( parent != null ) {
                return APILocator.getFileAssetAPI().moveFile( contentlet, parent, user, false );
            } else {
                return APILocator.getFileAssetAPI().moveFile( contentlet, host, user, false );
            }
        }

        File file = (File) InodeFactory.getInode( inode, File.class );

        // Checking permissions
        if ( !permissionAPI.doesUserHavePermission( file, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( "The user doesn't have the required permissions." );
        }

        refreshIndex(file, parent, user, host, null );

        if ( parent != null ) {
            return APILocator.getFileAPI().moveFile( file, parent, user, false );
        } else {
            return APILocator.getFileAPI().moveFile( file, host, user, false );
        }
    }

    public Map<String, Object> renameHTMLPage (String inode, String newName) throws Exception {

    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser(req);

        Identifier ident=APILocator.getIdentifierAPI().findFromInode(inode);
        IHTMLPage page = ident.getAssetType().equals("htmlpage") 
                           ? (IHTMLPage) InodeFactory.getInode(inode, HTMLPage.class)
                           : APILocator.getHTMLPageAssetAPI().fromContentlet(
                                 APILocator.getContentletAPI().find(inode, user, false));
        
    	HashMap<String, Object> result = new HashMap<String, Object> ();
    	
    	String pageURL = page.getPageUrl();
    	String lastName = (pageURL.lastIndexOf(".") > -1) ? pageURL.substring(0, pageURL.lastIndexOf(".")) : pageURL;
    	
    	result.put("lastName",lastName);

    	result.put("newName", newName);
    	result.put("inode", inode);
    	if (ident.getAssetType().equals("htmlpage") ? 
    	        HTMLPageFactory.renameHTMLPage((HTMLPage)page, newName, user)
    	      : APILocator.getHTMLPageAssetAPI().rename((HTMLPageAsset) page, newName, user)) {
        	result.put("result", 0);
    	} else {
        	result.put("result", 1);
        	if (page.isLocked())
        		result.put("errorReason", "The page is locked");
        	else
        		result.put("errorReason", "Another page with the same name already exists on this folder");
    	}
    	return result;
    }

    /**
     * Copies a given inode HTMLPage to a given folder
     *
     * @param inode     HTMLPage inode
     * @param newFolder This could be the inode of a folder or a host
     * @return true if success, false otherwise
     * @throws Exception
     */
    public boolean copyHTMLPage ( String inode, String newFolder ) throws Exception {

        HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser( req );

        Identifier ident=APILocator.getIdentifierAPI().findFromInode(inode);
        IHTMLPage page = ident.getAssetType().equals("htmlpage") 
                           ? (IHTMLPage) InodeFactory.getInode(inode, HTMLPage.class)
                           : APILocator.getHTMLPageAssetAPI().fromContentlet(
                                 APILocator.getContentletAPI().find(inode, user, false));

        // gets folder parent
        Folder parent = null;
        try {
            parent = APILocator.getFolderAPI().find( newFolder, user, false );
        } catch ( Exception ignored ) {
            //Probably what we have here is a host
        }

        Host host = null;
        if ( parent == null ) {//If we didn't find a parent folder lets verify if this is a host
            host = APILocator.getHostAPI().find( newFolder, user, false );
        }

        // Checking permissions
        String permissionsError = "The user doesn't have the required permissions.";
        if ( !permissionAPI.doesUserHavePermission( page, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( parent != null && !permissionAPI.doesUserHavePermission( parent, PERMISSION_CAN_ADD_CHILDREN, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( host != null && !permissionAPI.doesUserHavePermission( host, PERMISSION_CAN_ADD_CHILDREN, user ) ) {
            throw new DotRuntimeException( permissionsError );
        }

        if(ident.getAssetType().equals("htmlpage")) {
            if ( parent != null ) {
                HTMLPageFactory.copyHTMLPage( (HTMLPage) page, parent );
            } else {
                HTMLPageFactory.copyHTMLPage( (HTMLPage) page, host );
            }
        }
        else {
            Contentlet cont=APILocator.getContentletAPI().find(inode, user, false);
            if(parent!=null) {
                cont=APILocator.getContentletAPI().copyContentlet(cont, parent, user, false);
            }
            else {
                cont=APILocator.getContentletAPI().copyContentlet(cont, host, user, false);
            }
            
        }

        return true;
    }

    /**
     * Moves a given inode HTMLPage to a given folder
     *
     * @param inode     HTMLPage inode
     * @param newFolder This could be the inode of a folder or a host
     * @return true if success, false otherwise
     * @throws Exception
     */
    public boolean moveHTMLPage ( String inode, String newFolder ) throws Exception {

        HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser( req );

        Identifier ident=APILocator.getIdentifierAPI().findFromInode(inode);
        IHTMLPage page = ident.getAssetType().equals("htmlpage") 
                           ? (IHTMLPage) InodeFactory.getInode(inode, HTMLPage.class)
                           : APILocator.getHTMLPageAssetAPI().fromContentlet(
                                 APILocator.getContentletAPI().find(inode, user, false));

        // gets folder parent
        Folder parent = null;
        try {
            parent = APILocator.getFolderAPI().find( newFolder, user, false );
        } catch ( Exception ignored ) {
            //Probably what we have here is a host
        }

        Host host = null;
        if ( parent == null ) {//If we didn't find a parent folder lets verify if this is a host
            host = APILocator.getHostAPI().find( newFolder, user, false );
        }

        // Checking permissions
        String permissionsError = "The user doesn't have the required permissions.";
        if ( !permissionAPI.doesUserHavePermission( page, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( parent != null && !permissionAPI.doesUserHavePermission( parent, PERMISSION_CAN_ADD_CHILDREN, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( host != null && !permissionAPI.doesUserHavePermission( host, PERMISSION_CAN_ADD_CHILDREN, user ) ) {
            throw new DotRuntimeException( permissionsError );
        }

        if(ident.getAssetType().equals("htmlpage")) {
            if ( parent != null ) {
                return HTMLPageFactory.moveHTMLPage( (HTMLPage) page, parent, user );
            } else {
                return HTMLPageFactory.moveHTMLPage( (HTMLPage) page, host, user );
            }
        }
        else {
            if ( parent != null ) {
                return APILocator.getHTMLPageAssetAPI().move((HTMLPageAsset)page, parent, user);
            }
            else {
                return APILocator.getHTMLPageAssetAPI().move((HTMLPageAsset)page, parent, user);
            }
        }
    }

    public Map<String, Object> renameLink (String inode, String newName) throws Exception {

    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = null;
        try {
        	user = com.liferay.portal.util.PortalUtil.getUser(req);
        } catch (Exception e) {
            Logger.error(this, "Error trying to obtain the current liferay user from the request.", e);
            throw new DotRuntimeException ("Error trying to obtain the current liferay user from the request.");
        }

    	HashMap<String, Object> result = new HashMap<String, Object> ();
    	Link link = (Link) InodeFactory.getInode(inode, Link.class);
    	String oldName = link.getTitle();
    	result.put("lastName", oldName);
    	result.put("extension", "");
    	result.put("newName", newName);
    	result.put("inode", inode);
    	if (LinkFactory.renameLink(link, newName, user)) {
        	result.put("result", 0);
    	} else {
        	result.put("result", 1);
        	if (link.isLocked())
        		result.put("errorReason", "The link is locked");
        	else
        		result.put("errorReason", "Another link with the same name already exists on this folder");
    	}
    	return result;
    }

    /**
     * Copies a given inode Link to a given folder
     *
     * @param inode     Link inode
     * @param newFolder This could be the inode of a folder or a host
     * @return true if success, false otherwise
     * @throws Exception
     */
    public boolean copyLink ( String inode, String newFolder ) throws Exception {

        HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser( req );

        Link link = (Link) InodeFactory.getInode( inode, Link.class );

        // gets folder parent
        Folder parent = null;
        try {
            parent = APILocator.getFolderAPI().find( newFolder, user, false );
        } catch ( Exception ignored ) {
            //Probably what we have here is a host
        }

        Host host = null;
        if ( parent == null ) {//If we didn't find a parent folder lets verify if this is a host
            host = APILocator.getHostAPI().find( newFolder, user, false );
        }

        // Checking permissions
        String permissionsError = "The user doesn't have the required permissions.";
        if ( !permissionAPI.doesUserHavePermission( link, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( parent != null && !permissionAPI.doesUserHavePermission( parent, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( host != null && !permissionAPI.doesUserHavePermission( host, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        }

        if ( parent != null ) {
            LinkFactory.copyLink( link, parent );
        } else {
            LinkFactory.copyLink( link, host );
        }

        return true;
    }

    /**
     * Moves a given inode Link to a given folder
     *
     * @param inode     Link inode
     * @param newFolder This could be the inode of a folder or a host
     * @return true if success, false otherwise
     * @throws Exception
     */
    public boolean moveLink ( String inode, String newFolder ) throws Exception {

        HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser( req );

        Link link = (Link) InodeFactory.getInode( inode, Link.class );

        // gets folder parent
        Folder parent = null;
        try {
            parent = APILocator.getFolderAPI().find( newFolder, user, false );
        } catch ( Exception ignored ) {
            //Probably what we have here is a host
        }

        Host host = null;
        if ( parent == null ) {//If we didn't find a parent folder lets verify if this is a host
            host = APILocator.getHostAPI().find( newFolder, user, false );
        }

        // Checking permissions
        String permissionsError = "The user doesn't have the required permissions.";
        if ( !permissionAPI.doesUserHavePermission( link, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( parent != null && !permissionAPI.doesUserHavePermission( parent, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        } else if ( host != null && !permissionAPI.doesUserHavePermission( host, PERMISSION_WRITE, user ) ) {
            throw new DotRuntimeException( permissionsError );
        }

        if ( parent != null ) {
            return LinkFactory.moveLink( link, parent );
        } else {
            return LinkFactory.moveLink( link, host );
        }
    }

    /**
     * Publish a given asset, in case of html pages can be published only if there is not related to it
     * unpublished content.
     *
     * @param inode
     * @return
     * @throws Exception
     */
    public boolean publishAsset ( String inode ) throws Exception {

    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser(req);

        Identifier id  = APILocator.getIdentifierAPI().findFromInode(inode);
    	if (!permissionAPI.doesUserHavePermission(id, PERMISSION_PUBLISH, user)) {
            throw new DotRuntimeException( "The user doesn't have the required permissions." );
        }

        HTMLPageAsset htmlPageAsset = null;
        if ( id != null && id.getAssetType().equals( "contentlet" ) ) {
            Contentlet cont = APILocator.getContentletAPI().find( inode, user, false );

            //Verify if it is a HTMLPage, if not let do a normal contentlet publish
            if (cont.getStructure().getStructureType()==Structure.STRUCTURE_TYPE_HTMLPAGE) {
                htmlPageAsset = APILocator.getHTMLPageAssetAPI().fromContentlet( cont );
            } else {
                APILocator.getContentletAPI().publish( cont, user, false );
                return true;
            }
        }

        /*
		Verify if we have unpublish content related to this page
         */
		java.util.List relatedAssets = new java.util.ArrayList();
		Inode asset = null;
		if ( htmlPageAsset != null && InodeUtils.isSet( htmlPageAsset.getInode() ) ) {//Verify for HTMLPages as content
			relatedAssets = PublishFactory.getUnpublishedRelatedAssetsForPage( htmlPageAsset, relatedAssets, false, user, false );
		} else {

			asset = InodeFactory.getInode( inode, Inode.class );

			//Verify if it is a legacy HTMLPage and have unpublished content
			if ( (asset != null && InodeUtils.isSet( asset.getInode() ))
					&& asset instanceof HTMLPage ) {
				relatedAssets = PublishFactory.getUnpublishedRelatedAssets( asset, relatedAssets, user, false );
			}
		}

		//Only publish the content if there is not related unpublished content
		if ( (relatedAssets == null) || (relatedAssets.size() == 0) ) {

            /*
			Publishing the HTMLPage
             */
			if ( htmlPageAsset != null && InodeUtils.isSet( htmlPageAsset.getInode() ) ) {//Publish for the new HTMLPages as content

				if ( !permissionAPI.doesUserHavePermission( htmlPageAsset, PERMISSION_PUBLISH, user ) ) {
					throw new Exception( "The user doesn't have the required permissions." );
				}

				//Publish the page
				return PublishFactory.publishHTMLPage( htmlPageAsset, req );
			} else if ( asset != null && InodeUtils.isSet( asset.getInode() ) ) {//Publish for all the other asset types including legacy HTMLPages

				if ( !permissionAPI.doesUserHavePermission( asset, PERMISSION_PUBLISH, user ) ) {
					throw new Exception( "The user doesn't have the required permissions." );
				}

				//Publish the legacy HTMLPage
				return PublishFactory.publishAsset( asset, req );
			}

		} else {
			throw new Exception( "Related assets needs to be published" );
		}

        return false;
    }

    public boolean unPublishAsset (String inode) throws Exception {
    	HibernateUtil.startTransaction();
    	boolean ret = false;
    	try{
        	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        	User user = getUser(req);

        	Identifier id  = APILocator.getIdentifierAPI().findFromInode(inode);
        	if (!permissionAPI.doesUserHavePermission(id, PERMISSION_PUBLISH, user))
        		throw new DotRuntimeException("The user doesn't have the required permissions.");

        	if(id!=null && id.getAssetType().equals("contentlet")){
        		Contentlet cont  = APILocator.getContentletAPI().find(inode, user, false);
        		cont.getMap().put(Contentlet.DONT_VALIDATE_ME, true);
        		APILocator.getContentletAPI().unpublish(cont, user, false);
        		ret = true;
        	}else{
        		WebAsset asset = (WebAsset) InodeFactory.getInode(inode, Inode.class);
        		Folder parent = (Folder)folderAPI.findParentFolder(asset, user, false);
        		ret = WebAssetFactory.unPublishAsset(asset, user.getUserId(), parent);
        	}
        	HibernateUtil.commitTransaction();
    	}catch(Exception e){
    		ret = false;
    		HibernateUtil.rollbackTransaction();
    	}

    	return ret;
    }

    public boolean archiveAsset (String inode) throws Exception {

    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser(req);

        Identifier id  = APILocator.getIdentifierAPI().findFromInode(inode);
    	if (!permissionAPI.doesUserHavePermission(id, PERMISSION_PUBLISH, user))
    		throw new DotRuntimeException("The user doesn't have the required permissions.");

    	if(id!=null && id.getAssetType().equals("contentlet")){
    		Contentlet cont  = APILocator.getContentletAPI().find(inode, user, false);
    		APILocator.getContentletAPI().archive(cont, user, false);
    		return true;
    	}

        WebAsset asset = (WebAsset) InodeFactory.getInode(inode, Inode.class);

        if (!permissionAPI.doesUserHavePermission(asset, PERMISSION_WRITE, user))
			throw new DotRuntimeException("The user doesn't have the required permissions.");

        return WebAssetFactory.archiveAsset(asset, user.getUserId());
    }

    public boolean unArchiveAsset (String inode) throws Exception {

    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser(req);


        Identifier id  = APILocator.getIdentifierAPI().findFromInode(inode);
    	if (!permissionAPI.doesUserHavePermission(id, PERMISSION_PUBLISH, user))
    		throw new DotRuntimeException("The user doesn't have the required permissions.");

    	if(id!=null && id.getAssetType().equals("contentlet")){
    		Contentlet cont  = APILocator.getContentletAPI().find(inode, user, false);
    		APILocator.getContentletAPI().unarchive(cont, user, false);
    		return true;
    	}

        WebAsset asset = (WebAsset) InodeFactory.getInode(inode, Inode.class);


        if (!permissionAPI.doesUserHavePermission(asset, PERMISSION_WRITE, user))
			throw new DotRuntimeException("The user doesn't have the required permissions.");

        WebAssetFactory.unArchiveAsset(asset);

        return true;
    }


    public boolean unlockAsset (String inode) throws Exception {

    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser(req);


        Identifier id  = APILocator.getIdentifierAPI().findFromInode(inode);
    	if (!permissionAPI.doesUserHavePermission(id, PERMISSION_PUBLISH, user))
    		throw new DotRuntimeException("The user doesn't have the required permissions.");

    	if(id!=null && id.getAssetType().equals("contentlet")){
    		Contentlet cont  = APILocator.getContentletAPI().find(inode, user, false);
    		APILocator.getContentletAPI().unlock(cont, user, false);
    		return true;
    	}

        WebAsset asset = (WebAsset) InodeFactory.getInode(inode, Inode.class);

        if (!permissionAPI.doesUserHavePermission(asset, PERMISSION_WRITE, user))
			throw new DotRuntimeException("The user doesn't have the required permissions.");

        WebAssetFactory.unLockAsset(asset);
        return true;
    }

    public boolean deleteAsset(String inode) throws Exception
    {
    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser(req);


        Identifier id  = APILocator.getIdentifierAPI().findFromInode(inode);
    	if (!permissionAPI.doesUserHavePermission(id, PERMISSION_PUBLISH, user))
    		throw new DotRuntimeException("The user doesn't have the required permissions.");

    	if(id!=null && id.getAssetType().equals("contentlet")){
    		Contentlet cont  = APILocator.getContentletAPI().find(inode, user, false);
    		cont.getMap().put(Contentlet.DONT_VALIDATE_ME, true);
    		APILocator.getContentletAPI().delete(cont, user, false);
    		return true;
    	}


        WebAsset asset = (WebAsset) InodeFactory.getInode(inode, Inode.class);

        //I verify the permissions in the methods but I could change that
        //if (!PermissionFactory.doesUserHavePermission(asset, PERMISSION_WRITE, user))
		//	throw new DotRuntimeException("The user doesn't have the required permissions.");

        WebAssetFactory.deleteAsset(asset, user);
        return true;
    }



    public Map<String, Object> changeAssetMenuOrder (String inode, int newValue) throws ActionException, DotDataException {

    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = null;
        try {
        	user = com.liferay.portal.util.PortalUtil.getUser(req);
        } catch (Exception e) {
            Logger.error(this, "Error trying to obtain the current liferay user from the request.", e);
            throw new DotRuntimeException ("Error trying to obtain the current liferay user from the request.");
        }

    	HashMap<String, Object> result = new HashMap<String, Object> ();
    	Inode asset = (Inode) InodeFactory.getInode(inode, Inode.class);
    	if (asset instanceof Folder) {
    		Folder folder = (Folder) asset;
    		result.put("lastValue", folder.getSortOrder());
    		WebAssetFactory.changeAssetMenuOrder (asset, newValue, user);
    	} else {
    		result.put("lastValue", ((WebAsset)asset).getSortOrder());
    		WebAssetFactory.changeAssetMenuOrder (asset, newValue, user);
    	}
       	result.put("result", 0);
    	return result;
    }


    private User getUser(HttpServletRequest req) {

        // get the user
        User user = null;
        try {
            user = com.liferay.portal.util.PortalUtil.getUser(req);
        } catch (Exception e) {
            Logger.error(this, "Error trying to obtain the current liferay user from the request.", e);
            throw new DotRuntimeException ("Error trying to obtain the current liferay user from the request.");
        }
        return user;

    }


	/**
	 * This method returns the basic info of the full tree of hosts and folders
	 * @return
	 * @throws SystemException
	 * @throws PortalException
	 * @throws DotRuntimeException
	 * @throws DotDataException
	 * @throws DotSecurityException
	 */
    public List<Map<String,Object>> getTreeMinInfo () throws DotRuntimeException, PortalException, SystemException, DotDataException, DotSecurityException {
         return getTreeMinInfoByHostId("fullTree");
    }

	private List<Map<String,Object>> getFolderMinInfoTree (Folder parent, Role[] roles, String currentFullPath, String currentAbsolutePath) {

        FolderAPI folderAPI = APILocator.getFolderAPI();

        List<Map<String, Object>> toReturn = new ArrayList<Map<String,Object>>();

        List<Folder> children = new ArrayList<Folder>();
		try {
			children = folderAPI.findSubFolders(parent,userAPI.getSystemUser(),false);
		} catch (Exception e) {
			Logger.error(BrowserAjax.class,e.getMessage(),e);
		}

        for (Folder f : children) {
            Map<String, Object> folderMap = new HashMap<String, Object>();
            folderMap.put("type", "folder");
            folderMap.put("name", f.getName());
            folderMap.put("id", f.getInode());
            String fullPath = currentFullPath + "/" + f.getName();
            String absolutePath = currentAbsolutePath + "/" + f.getName();
            folderMap.put("fullPath", fullPath);
            folderMap.put("absolutePath", absolutePath);
            List<Map<String, Object>> childrenMaps = getFolderMinInfoTree (f, roles, fullPath, absolutePath);
            folderMap.put("children", childrenMaps);
            toReturn.add(folderMap);
        }

        return toReturn;
    }
	//http://jira.dotmarketing.net/browse/DOTCMS-3232
	/**
	 * This method returns the basic info of the tree of host and folders for the given hostId
	 * @return
	 * @throws SystemException
	 * @throws PortalException
	 * @throws DotRuntimeException
	 * @throws DotDataException
	 * @throws DotSecurityException
	 *
	 */
	public List<Map<String,Object>> getTreeMinInfoByHostId (String hostId) throws DotRuntimeException, PortalException, SystemException, DotDataException, DotSecurityException {

    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
    	WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
        FolderAPI folderAPI = APILocator.getFolderAPI();
        Host newHost = new Host();
        List<Host> hosts = new ArrayList<Host>();

        Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(user.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e1) {
			Logger.error(BrowserAjax.class,e1.getMessage(),e1);
		}

		List<Map<String,Object>> toReturn = new ArrayList<Map<String,Object>>();
		if(UtilMethods.isSet(hostId)&& hostId.equalsIgnoreCase("fullTree")) {
			   hosts = hostAPI.findAll(user, false);
		} else if(InodeUtils.isSet(hostId)) {
		   newHost = hostAPI.find(hostId, user, false);
		   hosts.add(newHost);
		} else {
		   newHost = hostWebAPI.getCurrentHost(ctx.getHttpServletRequest());
		   hosts.add(newHost);
		}
		Collections.sort(hosts, new HostNameComparator());  // DOTCMS JIRA - 4354

		for (Host host : hosts) {
			if(host.isSystemHost())
				continue;
			if(host.isArchived()==false){
				String currentPath = host.getHostname();
				Map<String,Object> hostMap = new HashMap<String, Object>();
				hostMap.put("type", "host");
				hostMap.put("name", host.getHostname());
				hostMap.put("id", host.getIdentifier());
				hostMap.put("fullPath", currentPath);
				hostMap.put("absolutePath", currentPath);
				List<Map<String, Object>> children = new ArrayList<Map<String,Object>>();

				List<Folder> subFolders = folderAPI.findSubFolders(host,user,false);
				for (Folder f : subFolders) {

						List permissions = new ArrayList();
						try {
							permissions = permissionAPI.getPermissionIdsFromRoles(f, roles, user);
						} catch (DotDataException e) {
							Logger.error(this, "Could not load permissions : ",e);
						}
						if(permissions.contains(PERMISSION_READ)){
							Map<String, Object> folderMap = new HashMap<String, Object>();
							folderMap.put("type", "folder");
							folderMap.put("name", f.getName());
							folderMap.put("id", f.getInode());
							String fullPath = currentPath + ":/" + f.getName();
							String absolutePath = "/" + f.getName();
							folderMap.put("fullPath", fullPath);
							folderMap.put("absolutePath", absolutePath);
							List<Map<String, Object>> childrenMaps = getFolderMinInfoTree(f, roles, fullPath, absolutePath);
							folderMap.put("children", childrenMaps);
							children.add(folderMap);
						}

				}
				hostMap.put("children", children);
				toReturn.add(hostMap);
			}
		}
		return toReturn;
	}

	private Map<String, Object> hostMap(Host host) {
    	String currentPath = host.getHostname();
        Map<String,Object> hostMap = new HashMap<String, Object>();
        hostMap.put("type", "host");
        hostMap.put("hostName", host.getHostname());
        hostMap.put("name", host.getHostname());
        hostMap.put("id", host.getIdentifier());
        hostMap.put("identifier", host.getIdentifier());
        hostMap.put("fullPath", currentPath);
        hostMap.put("absolutePath", currentPath);
        return hostMap;
	}

	private Map<String, Object> folderMap(Folder f) throws DotDataException, DotSecurityException {
    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
		HostAPI hostAPI = APILocator.getHostAPI();
		Map<String, Object> folderMap = new HashMap<String, Object>();
		folderMap.put("type", "folder");
		folderMap.put("name", f.getName());
		folderMap.put("id", f.getInode());
		folderMap.put("inode", f.getInode());
		folderMap.put("defaultFileType", f.getDefaultFileType());
		String currentPath = hostAPI.findParentHost(f, userWebAPI.getSystemUser(), false).getHostname();
		String fullPath = currentPath + ":/" + f.getName();
		String absolutePath = "/" + f.getName();
		folderMap.put("fullPath", fullPath);
		folderMap.put("absolutePath", absolutePath);
        return folderMap;
	}

	public List<Map<String, Object>> getHosts() throws PortalException, SystemException, DotDataException, DotSecurityException {
    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
    	WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
        Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(user.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e1) {
			Logger.error(BrowserAjax.class,e1.getMessage(),e1);
		}
        boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
		HostAPI hostAPI = APILocator.getHostAPI();
		List<Host> hosts = hostAPI.findAll(user, respectFrontendRoles);
		List<Map<String, Object>> hostsToReturn = new ArrayList<Map<String,Object>>(hosts.size());
		Collections.sort(hosts, new HostNameComparator());
		for (Host h: hosts) {
			List permissions = new ArrayList();
			try {
				permissions = permissionAPI.getPermissionIdsFromRoles(h, roles, user);
			} catch (DotDataException e) {
				Logger.error(this, "Could not load permissions : ",e);
			}
			if(permissions.contains(PERMISSION_READ)){
			    hostsToReturn.add(hostMap(h));
			}
		}
		return hostsToReturn;
	}


	public List<Map<String, Object>> getHostsByPermissions(String requiredPermissions) throws PortalException, SystemException, DotDataException, DotSecurityException {
    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
    	WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
        boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
		HostAPI hostAPI = APILocator.getHostAPI();
		List<Host> hosts = hostAPI.findAll(user, respectFrontendRoles);
		List<Map<String, Object>> hostsToReturn = new ArrayList<Map<String,Object>>(hosts.size());
		Collections.sort(hosts, new HostNameComparator());
		for (Host h: hosts) {
			if(UtilMethods.isSet(requiredPermissions)){
				if(permissionAPI.doesUserHavePermissions(h,requiredPermissions, user)){
					hostsToReturn.add(hostMap(h));
				}
			}
		}
		return hostsToReturn;
	}

	public List<Map<String, Object>> getHostsWithThemes() throws PortalException, SystemException, DotDataException, DotSecurityException {
    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
    	WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
		HostAPI hostAPI = APILocator.getHostAPI();

		// get hosts the user has read permissions on
		List<Host> hosts = hostAPI.getHostsWithPermission(com.dotmarketing.business.PermissionAPI.PERMISSION_READ, false, user, false);

		List<Map<String, Object>> hostsToReturn = new ArrayList<Map<String,Object>>(hosts.size());
		List<Host> filteredHosts = new ArrayList<Host>();

		for (Host h : hosts) {
			Folder folder = APILocator.getFolderAPI().findFolderByPath("/application/themes/", h , user, false);
			// add hosts who have /application/themes/ folder
			// add hosts the user has read permissions to the /application/themes/ folder
			if(UtilMethods.isSet(folder) && UtilMethods.isSet(folder.getName()) &&
					permissionAPI.doesUserHavePermissions(folder,"TEMPLATE_LAYOUTS:"+PermissionAPI.PERMISSION_READ, user)) {
				filteredHosts.add(h);
			}
		}

		Collections.sort(hosts, new HostNameComparator());
		for (Host h: filteredHosts) {
			if(permissionAPI.doesUserHavePermissions(h,"TEMPLATE_LAYOUTS:"+PermissionAPI.PERMISSION_READ, user)){
				hostsToReturn.add(hostMap(h));
			}
		}
		return hostsToReturn;
	}

	public List<Map<String, Object>> getHostsIncludeAll() throws PortalException, SystemException, DotDataException, DotSecurityException {
		UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
		WebContext ctx = WebContextFactory.get();
		User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
		Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(user.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e1) {
			Logger.error(BrowserAjax.class,e1.getMessage(),e1);
		}
		boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
		HostAPI hostAPI = APILocator.getHostAPI();
		List<Host> hosts = hostAPI.findAll(user, respectFrontendRoles);
		List<Map<String, Object>> hostsToReturn = new ArrayList<Map<String,Object>>(hosts.size());
		Collections.sort(hosts, new HostNameComparator());
		for (Host h: hosts) {
			List permissions = new ArrayList();
			try {
				permissions = permissionAPI.getPermissionIdsFromRoles(h, roles, user);
			} catch (DotDataException e) {
				Logger.error(this, "Could not load permissions : ",e);
			}
			if(permissions.contains(PERMISSION_READ)){
				hostsToReturn.add(hostMap(h));
			}
		}
		Host system = hostAPI.findSystemHost();
		List permissions = new ArrayList();
		try {
			permissions = permissionAPI.getPermissionIdsFromRoles(system, roles, user);
		} catch (DotDataException e) {
			Logger.error(this, "Could not load permissions : ",e);
		}
		if(permissions.contains(PERMISSION_READ)){
			Host allHosts = new Host();
			allHosts.setHostname("All Hosts");
			allHosts.setIdentifier("allHosts");
			hostsToReturn.add(hostMap(allHosts));
		}

		return hostsToReturn;
	}


	public List<Map<String, Object>> getHostSubfolders(String hostId) throws PortalException, SystemException, DotDataException, DotSecurityException {
		if(hostId.equals("allHosts")){
			return  new ArrayList<Map<String,Object>>();
		}
    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
    	WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
        Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(user.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e1) {
			Logger.error(BrowserAjax.class,e1.getMessage(),e1);
		}
        boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
		HostAPI hostAPI = APILocator.getHostAPI();
		Host host = hostAPI.find(hostId, user, respectFrontendRoles);
		FolderAPI folderAPI = APILocator.getFolderAPI();
		List<Folder> folders = folderAPI.findSubFolders(host,user,false);
		List<Map<String, Object>> foldersToReturn = new ArrayList<Map<String,Object>>(folders.size());
		for (Folder f: folders){
			List permissions = new ArrayList();
			try {
				permissions = permissionAPI.getPermissionIdsFromRoles(f, roles, user);
			} catch (DotDataException e) {
				Logger.error(this, "Could not load permissions : ",e);
			}
			if(permissions.contains(PERMISSION_READ)){
			     foldersToReturn.add(folderMap(f));
			}
		}
		return foldersToReturn;
	}

	public List<Map<String, Object>> getHostSubfoldersByPermissions(String hostId, String requiredPermissions) throws PortalException, SystemException, DotDataException, DotSecurityException {
    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
    	WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
        boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
		HostAPI hostAPI = APILocator.getHostAPI();
		Host host = hostAPI.find(hostId, user, respectFrontendRoles);
		FolderAPI folderAPI = APILocator.getFolderAPI();
		List<Folder> folders = folderAPI.findSubFolders(host,user,false);
		List<Map<String, Object>> foldersToReturn = new ArrayList<Map<String,Object>>(folders.size());
		for (Folder f: folders){
			if(UtilMethods.isSet(requiredPermissions)){
				if(permissionAPI.doesUserHavePermissions(f,requiredPermissions, user)){
					foldersToReturn.add(folderMap(f));
				}
			}
		}
		return foldersToReturn;
	}

	public List<Map<String, Object>> getFolderSubfolders(String parentFolderId) throws PortalException, SystemException, DotDataException, DotSecurityException {
		UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
		WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
        Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(user.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e1) {
			Logger.error(BrowserAjax.class,e1.getMessage(),e1);
		}
		FolderAPI folderAPI = APILocator.getFolderAPI();
		Folder parentFolder = folderAPI.find(parentFolderId,user,false);
		List<Folder> folders = folderAPI.findSubFolders(parentFolder,user,false);
		List<Map<String, Object>> foldersToReturn = new ArrayList<Map<String,Object>>(folders.size());
		for (Folder f: folders) {
			List permissions = new ArrayList();
			try {
				permissions = permissionAPI.getPermissionIdsFromRoles(f, roles, user);
			} catch (DotDataException e) {
				Logger.error(this, "Could not load permissions : ",e);
			}
			if(permissions.contains(PERMISSION_READ)){
			   foldersToReturn.add(folderMap(f));
			}
		}
		return foldersToReturn;
	}


	public List<Map<String, Object>> getFolderSubfoldersByPermissions(String parentFolderId, String requiredPermissions) throws PortalException, SystemException, DotDataException, DotSecurityException {
		UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
		WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
		FolderAPI folderAPI = APILocator.getFolderAPI();
		Folder parentFolder = folderAPI.find(parentFolderId,user,false);
		List<Folder> folders = folderAPI.findSubFolders(parentFolder,user,false);
		List<Map<String, Object>> foldersToReturn = new ArrayList<Map<String,Object>>(folders.size());
		for (Folder f: folders) {
			if(UtilMethods.isSet(requiredPermissions)){
				if(permissionAPI.doesUserHavePermissions(f,requiredPermissions, user)){
					foldersToReturn.add(folderMap(f));
				}
			}
		}
		return foldersToReturn;
	}

	public Map<String, Object> findHostFolder(String hostFolderId) throws PortalException, SystemException, DotDataException, DotSecurityException {
		try {
			if (InodeUtils.isSet(hostFolderId)) {
				UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
				WebContext ctx = WebContextFactory.get();
				User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
				boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
				HostAPI hostAPI = APILocator.getHostAPI();
				Host host = hostAPI.find(hostFolderId, user, respectFrontendRoles);
				if(host != null) {
					return hostMap(host);
				}

				host = hostAPI.findByName(hostFolderId, user, respectFrontendRoles);
				if(host != null) {
					return hostMap(host);
				}

				FolderAPI folderAPI = APILocator.getFolderAPI();
				Folder folder = folderAPI.find(hostFolderId,user,false);
				if(folder != null) {
					return folderMap(folder);
				}
			}
		} catch (Exception e) {
		}

		return null;
	}

	public Map<String, Object> getFolderMap(String folderId) throws PortalException, SystemException, DotSecurityException, DotDataException{
		if (InodeUtils.isSet(folderId)) {
			UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
			WebContext ctx = WebContextFactory.get();
			User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
			boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
			FolderAPI folderAPI = APILocator.getFolderAPI();
			Folder folder = null;
			try{
				folder = folderAPI.find(folderId,user,respectFrontendRoles);
			}catch(Exception e){
				Logger.warn(this, "Unable to find folder with the given id, looking for host");
			}
			if(folder != null) {
				return folderMap(folder);
			}else{
				Host host = APILocator.getHostAPI().find(folderId, user, respectFrontendRoles);
				if(host!=null && InodeUtils.isSet(host.getIdentifier())){
					Map<String, Object> folderMap = new HashMap<String, Object>();
					folderMap.put("type", "folder");
					folderMap.put("name", FolderAPI.SYSTEM_FOLDER);
					folderMap.put("id", FolderAPI.SYSTEM_FOLDER);
					folderMap.put("inode", FolderAPI.SYSTEM_FOLDER);
					Structure defaultFileAssetStructure = StructureCache.getStructureByName(FileAssetAPI.DEFAULT_FILE_ASSET_STRUCTURE_VELOCITY_VAR_NAME);
					folderMap.put("defaultFileType", defaultFileAssetStructure.getInode());
					folderMap.put("fullPath", host.getHostname() + ":/");
					folderMap.put("absolutePath", "/");
					return folderMap;
				}

			}

		}
		return null;
	}

	public List<Map<String, Object>> getHostThemes(String hostId) throws PortalException, SystemException, DotDataException, DotSecurityException {
		if(hostId.equals("allHosts")){
			return  new ArrayList<Map<String,Object>>();
		}
    	UserWebAPI userWebAPI = WebAPILocator.getUserWebAPI();
    	WebContext ctx = WebContextFactory.get();
        User user = userWebAPI.getLoggedInUser(ctx.getHttpServletRequest());
        Role[] roles = new Role[]{};
		try {
			roles = com.dotmarketing.business.APILocator.getRoleAPI().loadRolesForUser(user.getUserId()).toArray(new Role[0]);
		} catch (DotDataException e1) {
			Logger.error(BrowserAjax.class,e1.getMessage(),e1);
		}
        boolean respectFrontendRoles = userWebAPI.isLoggedToFrontend(ctx.getHttpServletRequest());
		HostAPI hostAPI = APILocator.getHostAPI();
		Host host = hostAPI.find(hostId, user, respectFrontendRoles);
		FolderAPI folderAPI = APILocator.getFolderAPI();
		List<Folder> folders = folderAPI.findThemes(host, user, respectFrontendRoles);
		List<Map<String, Object>> foldersToReturn = new ArrayList<Map<String,Object>>(folders.size());
		for (Folder f: folders){
			List permissions = new ArrayList();
			try {
				permissions = permissionAPI.getPermissionIdsFromRoles(f, roles, user);
			} catch (DotDataException e) {
				Logger.error(this, "Could not load permissions : ",e);
			}
			if(permissions.contains(PERMISSION_READ)){
			     foldersToReturn.add(folderMap(f));
			}
		}
		return foldersToReturn;
	}

	public void refreshIndex(File file, Folder parent, User user, Host host, Folder folder ) throws Exception {

		Folder srcFolder = folder;
		if(folder == null){
			srcFolder = APILocator.getFolderAPI().find(file.getParent(),user,false);

		}
     	// issues/1603 - refresh index for src Folder
        if (srcFolder!=null){
        	APILocator.getContentletAPI().refreshContentUnderFolder(srcFolder);
     	}

        if ( parent != null ) {
        	APILocator.getContentletAPI().refreshContentUnderFolder(parent);
        } else {
        	APILocator.getContentletAPI().refreshContentUnderHost(host);
        }
	}
	
	public Map<String, Object> getSelectedBrowserPath(){
		Map<String, Object> result = new HashMap<String, Object>();
		HttpSession session = WebContextFactory.get().getSession();
		if(UtilMethods.isSet(session.getAttribute(SELECTED_BROWSER_PATH_OBJECT)))
			return (Map<String, Object>) session.getAttribute(SELECTED_BROWSER_PATH_OBJECT);
		
		result.put("path", new String[]{"root"});
		result.put("currentFolder", null);
		return result;
	}
	
	public boolean deleteHTMLPagePreCheck(String htmlPageInode) throws Exception{
    	HttpServletRequest req = WebContextFactory.get().getHttpServletRequest();
        User user = getUser(req);


        Identifier id  = APILocator.getIdentifierAPI().findFromInode(htmlPageInode);
    	if (!permissionAPI.doesUserHavePermission(id, PERMISSION_PUBLISH, user))
    		throw new DotRuntimeException("The user doesn't have the required permissions.");

    	if(id!=null && id.getAssetType().equals("contentlet")){
    		for(Contentlet con : APILocator.getContentletAPI().getSiblings(id.getId())){
    			if(!con.getInode().equals(htmlPageInode) && con.isLive())
    				return false;
    		}    		
    		return true;
    	}else{
    		return true;
    	}
	}

}
