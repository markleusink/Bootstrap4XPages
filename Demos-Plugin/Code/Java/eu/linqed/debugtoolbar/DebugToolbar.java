package eu.linqed.debugtoolbar;

/*
 * <<
 * 
 * XPage Debug Toolbar
 * Copyright 2012,2013 Mark Leusink http://linqed.eu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this 
 * file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License
 * >> 
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Map.Entry;

import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.component.UIComponent;
import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;
import javax.faces.model.SelectItemGroup;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import lotus.domino.Database;
import lotus.domino.Name;
import lotus.domino.Session;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.Date;
import java.util.logging.Level;

import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NotesException;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.directory.DirectoryUser;
import com.ibm.xsp.designer.context.XSPContext;

public class DebugToolbar implements Serializable {

	private static final long serialVersionUID = 6348127836747732428L;

	public static final String		BEAN_NAME 				= "dBar";
	private static final String 	LOG_FILE_CONTENTS 	= "logFileContents";
	private static final String 	LOG_FILE_SELECTED 	= "logFileSelected";
	private static final int 		MAX_MESSAGES 			= 2000;		//maximum number of message held in this class
	private static final long  		LOG_FILE_SIZE_LIMIT_MB 	= 2;		//log file size limit (in MB)
	
	private static final String TEMP_VAR_NAME = "dBarTemp";
	
	private boolean toolbarVisible;		//(automatically) set to true if the toolbar is loaded, if set to false (default)
										//no messages are written to the toolbar's messages section
	
	private Vector<Message> messages;
	private boolean isDetailedTimings;
	 
	private boolean collapsed;
	private Vector<String> filterLogTypes;
	private String activeTab;
	private String requestContextPath;
	private String consolePath;
	private boolean showLists;
	private String toolbarColor;
	
	private Object dumpObject;
	
	private ArrayList<SelectItemGroup> logFileOptions;
	
	private boolean configLoaded;
	
	private SortedSet<String> sortedScopeContents;
	
	private long logFileModifiedRead;
	private ArrayList<String> logFileHistory;
	
	private String dominoDataDir;
	private String dominoProgramDir;
	
	private boolean inspectorShowHiddenComponents;		//show or hide components without an id
	private boolean inspectorSortAlphabetic;			//sort the component ids list alphabetically or in page order
	private ArrayList<String> inspectorComponentIdsOptions;	//list of component ids
	private String inspectorSelectedComponentId;				//selected component id
	private ArrayList<String> inspectorExpression;			//list of components of the current selected expression
	private boolean inspectorDeclaredOnly;
	private ArrayList<String> inspectorHistory;
	private boolean inspectorHideMethods;
	
	private transient Database dbCurrent;
	private transient Session session;
	private String currentDbFilePath;
	private String currentDbTitle;
	private String serverName;
	
	private Vector<String> userRoles;
	private String notesVersion;
	private String extLibVersion;
	private String effectiveUserName;
	private String userName;
	
	//variables for external log database
	private String logDbPath;			//path to the (OpenLog) database
	private boolean logEnabled;			//enable/ disable persistent logging
	private int logLevel;			//level of messages to log
	private boolean logDbValid;
	private transient Database dbLog;
	
	private static int LEVEL_ERROR = 0;
	private static int LEVEL_WARN = 1;
	private static int LEVEL_INFO = 2;
	private static int LEVEL_DEBUG = 3;
		
	@SuppressWarnings("unchecked")
	public DebugToolbar() {
		
		try {
			activeTab = "";		//default tab: none
			
			toolbarVisible = false;
			collapsed = false;
			messages = new Vector<Message>(); 
			filterLogTypes = new Vector<String>();
			showLists = true;
			toolbarColor = "#38375B";		//default background color
			
			configLoaded = false;
			
			requestContextPath = FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
			consolePath = requestContextPath + "/debugToolbarConsole.xsp";
			
			dominoDataDir = com.ibm.xsp.model.domino.DominoUtils.getEnvironmentString("Directory");		//path to the domino/data directory
			dominoProgramDir =  com.ibm.xsp.model.domino.DominoUtils.getEnvironmentString("NotesProgram");		//path to the domino program directory
			
			inspectorShowHiddenComponents = false;
			inspectorComponentIdsOptions = new ArrayList<String>();
			inspectorExpression = new ArrayList<String>();
			
			logEnabled = false;
			
			dbCurrent = getCurrentDatabase();
			currentDbFilePath = dbCurrent.getFilePath();
			currentDbTitle = dbCurrent.getTitle();
			serverName = dbCurrent.getServer();
			dbCurrent.recycle();
			
			XSPContext context = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
			
			userName = getSession().getUserName();
			effectiveUserName = getSession().getEffectiveUserName();
			userRoles = new Vector<String>(context.getUser().getRoles());
			
			notesVersion = getSession().getNotesVersion();
			
		} catch (Exception e) {

			e.printStackTrace();
		}
		
	}
	
	//retrieve an instance of this toolbar class
	public static DebugToolbar get() {
		return (DebugToolbar) resolveVariable(BEAN_NAME);
	}
	
	//dump the contents of any object to the toolbar
	public void dump(Object dumpObject) {
		
		try { 
			
			this.dumpObject = dumpObject;
			ValueBinding vb = FacesContext.getCurrentInstance().getApplication().createValueBinding("#{javascript:dBarHelper.dumpObject()}");
			String dumpedContents = vb.getValue(FacesContext.getCurrentInstance()).toString();
			this.info( dumpedContents, "dumped");
						
		} catch (Exception e) {
			
			this.error("could not dump object (" + dumpObject.getClass().toString() + ")", "dumped" );
			
		} finally {
			
			this.dumpObject = null;
		}
		
	}
	
	public Object getDumpObject() {
		return dumpObject;
	}
	
	public void init(boolean defaultCollapsed, String color) {

		toolbarVisible = true;
		
		if (!this.configLoaded){
			this.collapsed = defaultCollapsed;
			
			if (color != null ) {
				this.toolbarColor = color;
			}

			this.configLoaded = true;
		}
	}
	public void init(boolean defaultCollapsed) {
		this.init(defaultCollapsed, null);
	}
	
	public static boolean hideDetails( String entryName) {
		
		if (entryName.startsWith(DebugToolbar.BEAN_NAME)) {
			return true;
		} else if ( entryName.equals("expressionInfo") ||
				entryName.equals("expression")){
			return true;
		}

		return false;
		
	}
	
	
	public String getActiveTab() {
		return this.activeTab;
	}
	
	//change the active tab
	public void setActiveTab( String tabName ) {
		
		if (tabName.equals(this.activeTab) ) {		//open tab clicked: close it
			this.activeTab = "";
			
		} else {
			
			this.activeTab = tabName;
			
			if (tabName.indexOf("Scope")>-1) {
				readScopeContents();
			}
			
		}
	}
	
	public void openTab( String tabName) {
		this.activeTab = tabName;
	}
	
	public void close() {
		this.setActiveTab("");
	}
	
	
	public boolean scopeHasValues() {
		return this.getScopeContents().size()>0;
	}
	
	@SuppressWarnings("unchecked")
	public Set getScopeContents() {
		return sortedScopeContents;
	}
	
	@SuppressWarnings("unchecked")
	public void readScopeContents() {
		Map map = (Map) resolveVariable(activeTab);
		sortedScopeContents = new TreeSet<String>(map.keySet());
	}
	
	public void clearScopeContents(String type) {
		if (type.equals("both")) {
			this.clearApplicationScope();
			this.clearSessionScope();
		} else if (type.equals("sessionScope")) {
			this.clearSessionScope();
		} else {
			this.clearApplicationScope();
		}
	}
	
	// clear the contents of the sessionScope
	@SuppressWarnings("unchecked")
	public void clearSessionScope() {
		
		Map sessionScope = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
		
		Iterator it = sessionScope.keySet().iterator();
		while(it.hasNext() ) {
			
			String key = (String) it.next();
			if (!key.equals( BEAN_NAME )) {		//don't remove debug toolbar variable
				sessionScope.remove(key);
			}
			
		}

		// hide all tabs after clearing
		setActiveTab("");
	}

	// clear the contents of the applicationScope
	@SuppressWarnings("unchecked")
	public void clearApplicationScope() {
		try {

			Map applicationScope = FacesContext.getCurrentInstance().getExternalContext().getApplicationMap();
			
			Iterator it = applicationScope.keySet().iterator();
			while(it.hasNext() ) {
				applicationScope.remove(it.next());
			}
				
			setActiveTab("");
		} catch (Exception e) {
			this.error(e);
		}
	}
	
	//remove a specific entry from a scope
	@SuppressWarnings("unchecked")
	public void clearScopeEntry(String name) {
		
		if (StringUtil.isNotEmpty(name)) {

			Map map = (Map) resolveVariable(activeTab);
			map.remove(name);
		
			readScopeContents();
		}
	}
	
	public boolean isCollapsed() {
		return this.collapsed;
	}
	public void setCollapsed(boolean to) {
		this.collapsed = to;
	}
	
	//see above: used to hide/show the toolbar ("remove" link)
	public boolean isToolbarVisible() {
		return this.toolbarVisible;
	}
	public void setToolbarVisible(boolean to) {
		this.toolbarVisible = to;
	}
	
	/**
	 * Check the visibility of the toolbar - this determines wether messages are logged to the toolbar (at all)
	 *
	 * @deprecated use {@link isToolbarVisible()} instead  
	 */	
	public boolean isEnabled() {
		return this.toolbarVisible;
	}
	/**
	 * Set the visibility of the toolbar - this determines wether messages are logged to the toolbar (at all)
	 *
	 * @deprecated use {@link setToolbarVisible(booelean to)} instead  
	 */	
	public void setEnabled(boolean to) {
		this.toolbarVisible = to;
	}
	
	public boolean isShowLists() {
		return showLists;
	}
	public void setShowLists(boolean to) {
		this.showLists = to;
	}
	
	/*
	 * log a message with a specific type to the toolbar and/or external database
	 */
	private void log( Object msg, String msgContext, String type) {
		
		if (toolbarVisible || logEnabled) {
			
			Message message = new Message( msg, msgContext, type);
			
			addMessageToToolbar(message);
			
			//store log document
			createLogDocument( message, type.equals(Message.TYPE_ERROR) , Level.INFO);
			
		}

	}
	private void log( Throwable throwable, String msgContext) {

		if (toolbarVisible || logEnabled) {		//abort if toolbar not visible/ no external logging enabled
			
			Message message = new Message(throwable, msgContext);
			
			addMessageToToolbar(message);
			
			//store log document
			createLogDocument(message, true, Level.WARNING);
		}
		
	}
	
	//adds a message to the debug toolbar
	private void addMessageToToolbar(Message message) {
		try {
			
			if ( !toolbarVisible ) { return; }
	
			this.messages.add(0, message);
			
			if (messages.size() > DebugToolbar.MAX_MESSAGES) {
				messages.remove(messages.size() - 1); // remove oldest element
			}
			
		} catch (Exception e) {
			
			//error while logging to the toolbar: log to console
			System.err.println("(DebugToolbar) error while logging: " + e.getMessage());
			System.out.println("(DebugToolbar) " + message.getText() );
		}
	}
	
	public void info(Object msg) {
		this.log( msg, null, Message.TYPE_INFO);
	}
	public void info(Object msg, String msgContext) {
		this.log(msg, msgContext, Message.TYPE_INFO);
	}
	public void debug(Object msg) {
		this.log(msg, null, Message.TYPE_DEBUG);
	}
	public void debug(Object msg, String msgContext) {
		this.log(msg, msgContext, Message.TYPE_DEBUG);
	}
	public void warn(Object msg) {
		this.log(msg, null, Message.TYPE_WARNING);
	}
	public void warn(Object msg, String msgContext) {
		this.log(msg, msgContext, Message.TYPE_WARNING);
	}
	public void error(Throwable error) {
		this.log(error, null);
	}
	public void error(Throwable error, String msgContext) {
		this.log(error, msgContext);
	}
	public void error( Object error ) {
		this.error(error, null);
	}
	public void error( Object error, String msgContext) {
		this.log(error, msgContext, Message.TYPE_ERROR);
	}
	
	public void addDivider() {
		this.log("", null, Message.TYPE_DIVIDER);
	}

	// clear all debug log messages
	public void clearMessages() {
		messages.clear();
	}

	/*************************************
	 * INSPECTOR
	 ************************************/
	
	//return a string representation of the expression
	public String getInspectorExpression() {
		return DebugToolbar.join(inspectorExpression);
	}
	
	public static void addInspectorMessage(String message) {
		FacesContext.getCurrentInstance().addMessage("inspectorMessages",
			new FacesMessage( FacesMessage.SEVERITY_WARN, message, message) );
	}
	
	//split the expression on the dots, but not for dots in parentheses
	public void setInspectorExpression( String expression ) {
		String regex = "\\.(?![^(]*\\))";
		inspectorExpression = new ArrayList<String>( Arrays.asList(expression.split(regex)) );
		
		addExpressionToHistory();
	}
	public void addInspectorExpressionComponent( String c) {
		inspectorExpression.add(c);
		
		addExpressionToHistory();
	}
	
	//remove the last component of the expression
	public void removeExpressionComponent() {
		inspectorExpression.remove(inspectorExpression.size()-1);
	}
	public boolean isExpressionMultipleComponents() {
		return inspectorExpression.size()>1;
	}
	
	public String getInspectorShowHiddenComponents() {
		return (inspectorShowHiddenComponents ? "show" : "hide");
	}
	public void setInspectorShowHiddenComponents( String to) {
		inspectorShowHiddenComponents = to.equals("show");
	}

	public String getInspectorSelectedComponentId() {
		return inspectorSelectedComponentId;
	}
	public void setInspectorSelectedComponentId(String to) {
		this.inspectorSelectedComponentId = to; 
	}
	
	private void addExpressionToHistory() {
		if (inspectorHistory == null) {
			inspectorHistory = new ArrayList<String>();
		}
		
		if (!inspectorHistory.contains( getInspectorExpression())) {
			inspectorHistory.add(getInspectorExpression() );
		}
		
		if (inspectorHistory.size() > 50) {
			inspectorHistory.remove(inspectorHistory.size() - 1); // remove oldest element
		}
	}
	
	public ArrayList<String> getInspectorHistory() {
		return inspectorHistory;
	}
	
	//retrieve a list of sorted methods for a class
	@SuppressWarnings("unchecked")
	public ArrayList<Method> getSortedMethods( Class in) {
		
		ArrayList<Method> methods = null;
		
		try {
			Method[] classMethods = in.getMethods();
			methods = new ArrayList(Arrays.asList(classMethods));
			
			String className = in.getCanonicalName();
			
			if (inspectorDeclaredOnly) {		//filter: declared only (can't use getDeclaredXX here: will throw a Java security exception)
			
				for (Iterator<Method> iterator = methods.iterator(); iterator.hasNext();) {
					Method method = iterator.next();
					if (!method.getDeclaringClass().getCanonicalName().equals(className)) {
						iterator.remove();
					}
				}
			}
			
			Collections.sort( methods, new MethodComparable() );
		} catch (Exception e) {
			this.error(e);
		}
		
		return methods;
	}
	
	//sort methods by name, moving methods starting with an underscore to the end 
	private class MethodComparable implements Comparator<Method>{
	    public int compare(Method m1, Method m2) {
	    	String m1Name = m1.getName();
	    	String m2Name = m2.getName();
	    	
	    	if( m1Name.startsWith("_") && !m2Name.startsWith("_") ) { return 1; }
			if( !m1Name.startsWith("_") && m2Name.startsWith("_") ) { return -1; }
	    	return m1Name.compareTo(m2Name);
	    }
	}
	
	//retrieve a list of sorted fields for a class
	@SuppressWarnings("unchecked")
	public ArrayList<Field> getSortedFields( Class in) {
		
		ArrayList<Field> fields = null;
		
		try {
			Field[] classFields = in.getFields();
			fields = new ArrayList(Arrays.asList(classFields));
			String className = in.getCanonicalName();
			
			if (inspectorDeclaredOnly) {		//filter: declared only (can't use getDeclaredXX here: will throw a Java security exception)
				
				for (Iterator<Field> iterator = fields.iterator(); iterator.hasNext();) {
					Field field = iterator.next();
					if (!field.getDeclaringClass().getCanonicalName().equals(className)) {
						iterator.remove();
					}
				}
			}

			Collections.sort(fields, new FieldComparable() );
		} catch (Exception e) {
			this.error(e);
		}
		
		return fields;
	}

	//sort fields by name 
	private class FieldComparable implements Comparator<Field>{
	    public int compare(Field fld1, Field fld2) {
	    	return fld1.getName().compareTo(fld2.getName());
	    }
	}
	
	public String getInspectorLink( String className ) {
		
		String inspectorLink = "";
		
		try {
			
			String linkBase;
			String classPath;
			
			if ( className.startsWith("javax.faces") ) {
				
				linkBase = "http://download.oracle.com/docs/cd/E17802_01/j2ee/j2ee/javaserverfaces/1.1_01/docs/api/";
				classPath = className.replace( ".", "/" ) + ".html";
				inspectorLink = linkBase + classPath;
				
			} else if ( className.startsWith("java") ) {		//link to java api
			
				linkBase = "http://java.sun.com/javase/6/docs/api/";
				classPath = className.replace( ".", "/" ) + ".html";
				inspectorLink = linkBase + classPath;
				
			} else if ( className.startsWith("com.ibm") ) {			//link to IBM/XPages API
				
				linkBase = "http://public.dhe.ibm.com/software/dw/lotus/Domino-Designer/JavaDocs/XPagesExtAPI/8.5.2/";
				classPath = className.replace( ".", "/" ) + ".html";
				inspectorLink = linkBase + classPath;

			} else {		//generic Google link
				
				inspectorLink = "http://google.com/search?q=" + className;

			}
			
		} catch (Exception e) { }
		
		return inspectorLink;

	}
	
	
	public boolean isInspectorDeclaredOnly() {
		return inspectorDeclaredOnly;
	}
	public void setInspectorDeclaredOnly(boolean apiDeclaredOnly) {
		this.inspectorDeclaredOnly = apiDeclaredOnly;
		this.inspectorHideMethods = false;
	}
	
	public boolean isInspectorHideMethods() {
		return inspectorHideMethods;
	}
	public void setInspectorHideMethods(boolean to) {
		inspectorHideMethods = to;
	}

	// check if the selected type is filtered from the list of messages
	public boolean isFiltered(String type) {
		return filterLogTypes.contains(type);
	}

	// add or remove the selected type to the list of filtered message types
	public void toggleFiltered(String type, boolean included) {
		
		if (included) {

			if (filterLogTypes.contains(type)) {
				filterLogTypes.remove(type);

			}

		} else {

			if (!filterLogTypes.contains(type)) {
				filterLogTypes.add(type);
			}

		}
	}

	// filter the list of messages and return it
	public Vector<Message> getFilteredMessages() {
		
		if (filterLogTypes.size() == 0) {

			return messages;

		} else {

			Vector<Message> filtered = new Vector<Message>();

			Iterator<Message> messagesIt = messages.iterator();
			while (messagesIt.hasNext()) {

				Message m = messagesIt.next();

				if (!filterLogTypes.contains(m.getType())) {

					filtered.add(m);

				}

			}

			return filtered;
		}

	}

	public Vector<Message> getMessages() {
		return messages;
	}

	public boolean isDetailedTimings() {
		return isDetailedTimings;
	}
	public void setDetailedTimings(boolean to) {
		this.isDetailedTimings = to;
	}

	public boolean hasMessages() {
		return messages.size() > 0;
	}

	/**
	 * Start a timer
	 *
	 * @deprecated use {@link debug(String msg)} instead  
	 */	
	public void startTimer(String id) {
		this.debug("(timer started) " + id);
	}

	/**
	 * Stop a timer
	 *
	 * @deprecated use {@link debug(String msg)} instead  
	 */	
	public void stopTimer(String id) {
		this.debug("(timer stopped) " + id);
	}
	
	public static String getReadableSize( long bytes ) {
		
		if (bytes < 1024) {
			return bytes + " bytes";
		} else {
			double kbSize = bytes / 1024;

			
			if (kbSize < 1024) {
				//0 decimals
				return  Math.round(kbSize*1)/1 + " kB";
			} else {
				//1 decimal
				double mbSize = kbSize / 1024;
				NumberFormat df = new DecimalFormat("#0.0");
				return df.format(mbSize) + " MB";	
			}
		}
		
	}
	
	public String getConsolePath() {
		return consolePath;
	}
 	
	
	@SuppressWarnings("unchecked")
	public ArrayList<Entry<String, String>> getCustomVars() {

		ArrayList customVars = new ArrayList<Entry<String,String>>();

		try {

			XSPContext context = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
			
			DirectoryUser currentUser = context.getUser();

			Name n = getSession().createName(effectiveUserName);
			Vector<String> groups = new Vector(currentUser.getGroups());
			groups.remove(effectiveUserName);
			groups.remove(n.getCommon());
			
			extLibVersion = getExtLibVersion();
			
			ExternalContext extCon = FacesContext.getCurrentInstance().getExternalContext();
			
			HttpServletRequest httpServletRequest = (HttpServletRequest) extCon.getRequest();
			
			customVars.add( new AbstractMap.SimpleEntry("title", "User"));
			customVars.add( new AbstractMap.SimpleEntry("username", effectiveUserName));
			customVars.add( new AbstractMap.SimpleEntry("access level", getReadableAccessLevel(getCurrentDatabase().getCurrentAccessLevel())));
			
			customVars.add( new AbstractMap.SimpleEntry("roles", (userRoles.size()>0 ? userRoles.toString() : "(no roles enabled)") ));
			customVars.add( new AbstractMap.SimpleEntry("groups", (groups.size()>0 ? groups.toString() : "(user not listed in any group)")));

			customVars.add( new AbstractMap.SimpleEntry("title", "Browser"));
			customVars.add( new AbstractMap.SimpleEntry("name", context.getUserAgent().getBrowser() + " " + context.getUserAgent().getBrowserVersion()));
			customVars.add( new AbstractMap.SimpleEntry("user agent", context.getUserAgent().getUserAgent()));
			customVars.add( new AbstractMap.SimpleEntry("language", context.getLocale().getDisplayName()	+ " (" + context.getLocaleString() + ")"));
			customVars.add( new AbstractMap.SimpleEntry("timezone", context.getTimeZoneString()));

			customVars.add( new AbstractMap.SimpleEntry("title", "Server") );
			customVars.add( new AbstractMap.SimpleEntry("name", getCurrentDatabase().getServer() ) );
			customVars.add( new AbstractMap.SimpleEntry("version", notesVersion ) );
			customVars.add( new AbstractMap.SimpleEntry("platform", getSession().getPlatform() ) );
			customVars.add( new AbstractMap.SimpleEntry("extension library version", extLibVersion ) );
			
			customVars.add( new AbstractMap.SimpleEntry("title", "Database") );
			customVars.add( new AbstractMap.SimpleEntry("name", currentDbTitle ) );
			customVars.add( new AbstractMap.SimpleEntry("path", currentDbFilePath ) );
			
			
			customVars.add( new AbstractMap.SimpleEntry("size", DebugToolbar.getReadableSize( (long) getCurrentDatabase().getSize())));
			customVars.add( new AbstractMap.SimpleEntry("created", getCurrentDatabase().getCreated().toString()) );
			customVars.add( new AbstractMap.SimpleEntry("last modified", getCurrentDatabase().getLastModified().toString()));
			customVars.add( new AbstractMap.SimpleEntry("full text indexed?", (getCurrentDatabase().isFTIndexed() ? "yes (last update: " + getCurrentDatabase().getLastFTIndexed().toString() + ")" : "no")));

			customVars.add( new AbstractMap.SimpleEntry("title", "Request"));
			customVars.add( new AbstractMap.SimpleEntry("query string", context.getUrl().getQueryString()));
			customVars.add( new AbstractMap.SimpleEntry("remote address", httpServletRequest.getRemoteAddr()));
			
			customVars.add( new AbstractMap.SimpleEntry("cookies", getCookieDump(extCon.getRequestCookieMap()) ));

		} catch (Exception e) {
			error(e, "getCustomVars");

		}

		return customVars;

	}
	
	//retrieve the version of the extension library using reflection (since it might not be installed)
	@SuppressWarnings("unchecked")
	private static String getExtLibVersion() {
		
		try {
			
			Class c = Class.forName("com.ibm.xsp.extlib.util.ExtLibUtil");
			Method m = c.getMethod("getExtLibVersion");
			return (String) m.invoke(null);
			
		} catch (Exception e) {
			return "not activated or installed";
		}
	      
	}
	
	private static String getCookieDump( Map<String,Cookie> cookieMap) {
		
		StringBuilder sb = new StringBuilder();

		sb.append("<table class=\"dumped\"><tbody>");

		for (Cookie cookie : cookieMap.values()) {
			sb.append("<tr><td>" + cookie.getName() + "</td><td>" + cookie.getValue() + "</td></tr>");
		}
		
		sb.append("</tbody></table>");
		
		return sb.toString();
	
	}
	
	private static String getReadableAccessLevel(int level) {

		switch (level) {
			case 1:		return "Depositor";
			case 2:		return "Reader";
			case 3:		return "Author";
			case 4:		return "Editor";
			case 5:		return "Designer";
			case 6:		return "Manager";
		}
		return "";
	}
	
	public String getColor() {
		return toolbarColor;
	}
	public void setColor(String color) {
		this.toolbarColor = color;
	}
	
	@SuppressWarnings("unchecked")
	private String getTempVar(String varName) {
		Map<String,Object> viewScope = (Map<String,Object>) resolveVariable("viewScope");
		
		if (viewScope.containsKey(TEMP_VAR_NAME)) {
			return ( (HashMap<String,String>) viewScope.get(TEMP_VAR_NAME)).get(varName);
		}
		return null;
		
	}
	
	//store a (temporary) dBar variable in the viewScope
	@SuppressWarnings("unchecked")
	private void setTempVar(String varName, String varContent) {
		
		Map<String,Object> viewScope = (Map<String,Object>) resolveVariable("viewScope");
		
		HashMap<String,String> tempVar = null;
		if (viewScope.containsKey(TEMP_VAR_NAME)) {
			tempVar = (HashMap) viewScope.get(TEMP_VAR_NAME);
		} else {
			tempVar = new HashMap<String,String>();
		}
		
		tempVar.put(varName, varContent);
		viewScope.put(TEMP_VAR_NAME, tempVar);
		
	}
	
	/*
	 * read components ids from the view root
	 */
	public ArrayList<String> getComponentIds() {
		
		try {
			
			inspectorComponentIdsOptions.clear();
			
			getComponentChildren( (com.ibm.xsp.component.UIViewRootEx2) resolveVariable("view") , "");
			
			if (inspectorSortAlphabetic) {
				Collections.sort(inspectorComponentIdsOptions);
			}
			
		} catch (Exception e) {
			error(e);
		}
		
		return inspectorComponentIdsOptions;
		
	}
	
	//walk the node tree and collect all component ids
	@SuppressWarnings("unchecked")
	private void getComponentChildren(UIComponent component, String prefix) {
	
		List<UIComponent> children = component.getChildren();
		
		if (inspectorComponentIdsOptions.size()>0 && !inspectorSortAlphabetic) {
			prefix += "- ";
		}
		
		if (children.size()>0) {

			for (UIComponent childNode : children) {
				String id = childNode.getId();
				
				if (id!=null) {
					
					if ( !"debugToolbar".equals(id) ) {		//don't add (children of) debug toolbar)
						
						if ( !id.startsWith("_") || inspectorShowHiddenComponents ) {
							//add to list
							inspectorComponentIdsOptions.add( prefix + id + "|" + id);
						}
					}
				}				
				
				if ( !"debugToolbar".equals(id) ) {
					getComponentChildren( childNode, prefix);
				}
			}
			
		}
	}
	
	public boolean isInspectorSortAlphabetic() {
		return inspectorSortAlphabetic;
	}
	public void setInspectorSortAlphabetic(boolean to) {
		this.inspectorSortAlphabetic = to;
	}

	//class to hold information about a file
	private class FileInfo implements Serializable, Comparable<Object> {
	
		private static final long serialVersionUID = -120581900512974320L;
		String name;
		Long modified;
		long size;

		public FileInfo(String name, long modified, long size) {
			this.name = name;
			this.modified = new Long(modified);
			this.size = size;
		}
		
		public String getName() {
			return name;
		}

		public Long getModified() {
			return modified;
		}

		public String getDescription() {
			return name + " (" + DebugToolbar.getReadableSize(size) + ") ";
		}

		public int compareTo(Object argInfo) {
			return ((FileInfo) argInfo).getModified().compareTo( this.getModified());
		}
	
	}
	
	//returns a list of all available log files
	public ArrayList<SelectItemGroup> getLogFileOptions() {
		
		try {
			
			if (logFileOptions==null || logFileOptions.size()==0) {

				logFileOptions = new ArrayList<SelectItemGroup>();
				
				//files in workspace/logs folder (e.g. error-log files)
				
				//error logs
				logFileOptions.add( getFilesOptions( "domino" + File.separator + "workspace" + File.separator + "logs" + File.separator,
						"Error (logs)",
						"error",
						null) );
				
				//trace logs
				logFileOptions.add( getFilesOptions( "domino" + File.separator + "workspace" + File.separator + "logs" + File.separator,
						"Trace (logs)",
						"trace",
						null) );
				
				//other files
				logFileOptions.add( getFilesOptions( "domino" + File.separator + "workspace" + File.separator + "logs" + File.separator,
						"Other (logs)",
						null,
						new ArrayList<String>(Arrays.asList( ("error,trace").split(",") ))  ) );

				//files in IBM_TECHNICAL_SUPPORT folder
				
				//xpages logs
				logFileOptions.add( getFilesOptions( "IBM_TECHNICAL_SUPPORT" + File.separator,
						"XPages (IBM_TECHNICAL_SUPPORT)",
						"xpages",
						null) );
				
				//console logs
				logFileOptions.add( getFilesOptions( "IBM_TECHNICAL_SUPPORT" + File.separator,
						"Console (IBM_TECHNICAL_SUPPORT)",
						"console", 
						null) );
				
				logFileOptions.add( getFilesOptions( "IBM_TECHNICAL_SUPPORT" + File.separator,
						"Other (IBM_TECHNICAL_SUPPORT)",
						null,
						new ArrayList<String>(Arrays.asList( ("xpages,console").split(",") ))  ) );
				
				//other files
				ArrayList<SelectItem> groupItems = new ArrayList<SelectItem>();
				groupItems.add( new SelectItem( dominoProgramDir + File.separator + "notes.ini", "notes.ini") );
				groupItems.add( new SelectItem( dominoProgramDir + File.separator + "jvm" + File.separator + "lib" + File.separator + "security" + File.separator + "java.policy", "java.policy") );
				logFileOptions.add( new SelectItemGroup( "other files", null, true, groupItems.toArray( new SelectItem[ groupItems.size() ] ) ) );
				
				
			}
			
		} catch (Exception e) {
			this.error(e);
		}
		
		return logFileOptions;
	}
	
	public void clearLogFileOptions() {
		if (logFileOptions != null) { logFileOptions.clear(); }
	}
	
	//retrieve a list of files from the specified folder relative to the domino\data folder (as a SelectItemGroup)
	//optionally with an include/ exclude filter
	private SelectItemGroup getFilesOptions( String folder, String groupTitle, String prefixInclude, ArrayList<String> prefixExclude) {
		
		ArrayList<SelectItem> groupItems = new ArrayList<SelectItem>();
		ArrayList<FileInfo> logFilesInfoList = getFilesList( folder, prefixInclude, prefixExclude);
			
		for (FileInfo info : logFilesInfoList) {
			groupItems.add( new SelectItem( dominoDataDir + File.separator + folder + info.getName(), info.getDescription()) );
		}
		
		return new SelectItemGroup( groupTitle, null, true, groupItems.toArray( new SelectItem[ groupItems.size() ] ) );
	}
	
	private ArrayList<FileInfo> getFilesList( String folder, String prefixInclude, ArrayList<String> prefixExclude ) {
		
		//list of included file extensions for log folders
		ArrayList<String> includeExt = new ArrayList<String>();
		includeExt.add("xml");
		includeExt.add("log");
		includeExt.add("txt");
		
		File dir = new File( dominoDataDir + File.separator + folder);
		
		ArrayList<FileInfo> filesInfoList = new ArrayList<FileInfo>();
		
		for (File f : dir.listFiles()) {
			
			if ( !f.isDirectory() && f.length()>0 ) {
				
				String name = f.getName();
				String ext = name.substring( name.lastIndexOf(".")+1 ).toLowerCase();
				
				if (includeExt.contains(ext)) {
					
					boolean include = true;
					
					if (prefixInclude != null) {
						include = f.getName().startsWith(prefixInclude);
						
					} else if (prefixExclude != null) {
						
						for (String prefix : prefixExclude) {
							if (f.getName().startsWith(prefix)) {
								include = false;
								break;
							}
						}
					}
					
					if (include) {
						filesInfoList.add( new FileInfo( f.getName(), f.lastModified(), f.length()));
					}
				}
			}
		}
		
		//sort the list of files descending by last modified
		Collections.sort( filesInfoList);

		return filesInfoList;
		
	}
	
	public String getSelectedLogFile() {
		return getTempVar(LOG_FILE_SELECTED);
	}
	public void setSelectedLogFile(String to) {
		setTempVar(LOG_FILE_SELECTED, to);
	}
	public String getLogFileContents() {
		return getTempVar(LOG_FILE_CONTENTS);
	}
	public void setLogFileContents(String to) {
		setTempVar(LOG_FILE_CONTENTS, to);
	}
	
	public boolean hasRecentFiles() {
		return (logFileHistory != null && logFileHistory.size()>0);
	}
	
	private void addToFileHistory(String fileName) {
		if (logFileHistory==null) {
			logFileHistory = new ArrayList<String>();
		}
		
		if (!logFileHistory.contains(fileName)) {
			
			if (logFileHistory.size() >= 5) {		//store last 5 files only
				logFileHistory.remove(logFileHistory.size() - 1); // remove oldest element
			}
			
			logFileHistory.add(0, fileName);
		}
		
	}
	
	public ArrayList<String> getLogFileHistory() {
		return logFileHistory;
	}
	
	/*
	 * Reads the selected log file and stores the contents in the viewScope 
	 */
	public void showFileContents() {
		
		BufferedReader reader = null;
		
		try {
			
			String selected = getSelectedLogFile();
			
			if (StringUtil.isEmpty(selected)) {		//no file selected
				setLogFileContents(null);
				return;
			}
			
			//add selected file to history
			addToFileHistory(selected);
			
			String contents = null;
		
			File f = new File(selected);
			if (f.length() > (LOG_FILE_SIZE_LIMIT_MB * 1024 * 1024) )  {		//file too large
				
				contents = "Files is too large: size is " + (f.length()/1024/1024) + " MB (limit is " + LOG_FILE_SIZE_LIMIT_MB + " MB)";
				
			} else {
				
				logFileModifiedRead = f.lastModified();
			
				StringBuffer fileContents = new StringBuffer(1000);
				reader = new BufferedReader( new InputStreamReader(new FileInputStream(f), "UTF-8") );
				char[] buffer = new char[1024];
				int read=0;
	
				while ( (read=reader.read(buffer)) != -1){
				    String readData = String.valueOf(buffer, 0, read);
				    fileContents.append(readData);
				    buffer = new char[1024];
				}
				
				//escape less then /greater then characters
				contents = fileContents.toString()
					.replace("<", "&lt;")
					.replace(">", "&gt;");
			}
			
			setLogFileContents(contents);
			
		} catch (Exception e) {
			error(e);
		} finally {
			try {
				reader.close();
			} catch (Exception e) { }
		}
		
	}
	
	//checks if the selected file is the most recent version
	public boolean isLogFileMostRecent() {
		
		String selected = getSelectedLogFile();
		
		if (StringUtil.isEmpty(selected) ) {
			return true;
		} else { 
			
			File f = new File(selected);
			long lastModified = f.lastModified();
			
			return (logFileModifiedRead >= lastModified);
		}
		
	}
	
	/*************************************
	 * EXTERNAL DB LOGGING
	 ************************************/

	public String getLogDbPath() {
		return logDbPath;
	}
	public void setLogDbPath(String to) {
		
		try {
			this.logDbValid = true;		//reset
			
			if (to.equals("current")) {
				this.logDbPath = getCurrentDatabase().getFilePath();
			} else {
				this.logDbPath = to;
			}
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}
	
	public boolean getLogDbValid() {
		return logDbValid;
	}
	
	public boolean isLogEnabled() {
		return logEnabled;
	}
	public void setLogEnabled(boolean logDbEnabled) {
		this.logEnabled = logDbEnabled;
	}
	
	public String getLogLevel() {
		if (logLevel==LEVEL_ERROR) {
			return "error";
		} else if (logLevel==LEVEL_WARN) {
			return "warn";
		} else if (logLevel==LEVEL_DEBUG) {
			return "debug";
		} else if (logLevel==LEVEL_INFO) {
			return "info";
		} else {
			return "?";
		}
	}
	public void setLogLevel(String level) {
		
		//check for valid input
		if (level.equals("all") || level.equals("debug") ) {
			logLevel = LEVEL_DEBUG;
		} else if ( level.equals("info") ) {
			logLevel = LEVEL_INFO;
		} else if ( level.equals("warn") ) {
			logLevel = LEVEL_WARN;
		} else if ( level.equals("error") ) {
			logLevel = LEVEL_ERROR;
		} else {
			logLevel = LEVEL_ERROR;		//default if no valid level set
		}

	}

	private static boolean isRecycled( Database db) {
		
		boolean isRecycled = true;
	    
	    try {

		    @SuppressWarnings("unused")
			int t = db.getCurrentAccessLevel();	
		    isRecycled = false;
		    
	    } catch (NotesException ne) {
	    	//ne.id == 4376		//object has been removed or recycled
	    }
	    
	    return isRecycled;
	}
	
	//retrieve the log database object
	private Database getLogDb() {
		try {
			
			if (!logDbValid) {		//db marked as invalid
				return null;
			}
			
			if (StringUtil.isEmpty(logDbPath)) {
				logDbValid = false;
				return null;
			}
			
			if ( dbLog == null || DebugToolbar.isRecycled(dbLog) ) {
				
				dbLog = getSession().getDatabase( serverName, logDbPath );
				
				if (dbLog == null) {		//still null: mark as invalid
					logDbValid = false;
				}
				
				if (!dbLog.isOpen()) {		//database object could not be opened: mark as invalid
					dbLog = null;
					logDbValid = false;
				}
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return dbLog;
	}
	
	//create a document in an external log database
	private void createLogDocument( Message message, boolean isError, Level severity) {
		
		if ( !logEnabled ) {
			return;
		}
		
		//check if we need to log this message, based on the log level set 
		int minLogLevel = 0;
		
		if (message.getType().equals(Message.TYPE_ERROR)) {
			minLogLevel = LEVEL_ERROR;
		} else if (message.getType().equals(Message.TYPE_WARNING)) {
			minLogLevel = LEVEL_WARN;
		} else if (message.getType().equals(Message.TYPE_INFO)) {
			minLogLevel = LEVEL_INFO;
		} else if (message.getType().equals(Message.TYPE_DEBUG)) {
			minLogLevel = LEVEL_DEBUG;
		}
		
		if (logLevel < minLogLevel) {
			return;
		}
		
		Document docLog = null;

		try {
			
			getLogDb();
			
			//abort if log db could not be opened/ not specified
			if (!logDbValid) {
				return;
			}
			
			docLog = getLogDb().createDocument();

			docLog.replaceItemValue("Form", "LogEvent");
			docLog.replaceItemValue("LogAgentLanguage", "XPages");
			docLog.replaceItemValue("LogUserName", userName );
			docLog.replaceItemValue("LogEffectiveName", effectiveUserName );

			if (isError) {
				docLog.replaceItemValue("LogErrorMessage", message.getSummary() );
				
				if (message.getErrorId()>0) {
					docLog.replaceItemValue("LogErrorNumber", message.getErrorId() );
				}
	
				docLog.replaceItemValue("LogStackTrace", message.getStackTrace());
				docLog.replaceItemValue("LogErrorLine", message.getErrorLine() );
				docLog.replaceItemValue("LogFromMethod", message.getErrorClassName() + "." + message.getErrorMethod() );
			}
			
			docLog.replaceItemValue("LogSeverity", severity.getName());
			
			setDate( docLog, "LogEventTime", message.getDate() );
			setDate( docLog, "LogAgentStartTime", message.getDate() );
			
			docLog.replaceItemValue("LogEventType", (isError ? "Error" : "Event" ) );
			docLog.replaceItemValue("LogMessage", message.getDetails() );
			docLog.replaceItemValue("LogFromDatabase", currentDbFilePath );
			docLog.replaceItemValue("LogFromServer", serverName);
			
			String fromPage = message.getFromPage();
			if (fromPage == null) {
				fromPage = ((com.ibm.xsp.component.UIViewRootEx2) resolveVariable("view")).getPageName();
				if (fromPage.indexOf("/")==0) {
					fromPage = fromPage.substring(1);
				}
			}
			
			docLog.replaceItemValue("LogFromAgent", fromPage);
			
			docLog.replaceItemValue("LogAccessLevel", getReadableAccessLevel( getCurrentDatabase().getCurrentAccessLevel() ) );
			docLog.replaceItemValue("LogUserRoles", userRoles );
			docLog.replaceItemValue("LogClientVersion", notesVersion );

			docLog.save(true);
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				docLog.recycle();
			} catch (Exception e) { }
		}

	}
	
	/******************************************
	 * utility functions
	 *****************************************/
	
	private Session getSession() {
		if (session == null) {
			session = (Session) resolveVariable("session");
		} else {
			try {
				@SuppressWarnings("unused")
				boolean tmp = session.isOnServer();
			} catch (NotesException ne) {
				try {
					session = (Session) resolveVariable("session");
				} catch (Exception e) {	}
			}
		}
		return session;
	}
	
	private Database getCurrentDatabase() {
		if ( dbCurrent == null || DebugToolbar.isRecycled(dbCurrent) ) {
			dbCurrent = (Database) resolveVariable("database");
		}
		return dbCurrent;
	}
	
	private static String join( Collection<String> c) {
		
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = c.iterator();
		while ( it.hasNext() ) {
			sb.append( it.next());
			
			if (it.hasNext()) {
				sb.append(".");
			}
		}
		return sb.toString();
	}
	
	private static Object resolveVariable (String variableName) {
		FacesContext context = FacesContext.getCurrentInstance();
		return context.getApplication().getVariableResolver().resolveVariable(context, variableName);
    }
	
	//function to store a java.util.Date in a document field (and recycle the DateTime object afterwards)
	private void setDate( Document doc, String dateItemName, Date date) {
		
		if (date==null) { return; }
		
		DateTime dt = null;
		
		try {
			
			dt = getSession().createDateTime(date);
			doc.replaceItemValue(dateItemName, dt);
			
		} catch (NotesException e) {
			error(e);
		} finally {
			try {
                dt.recycle();
            } catch (NotesException ne) { }
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static ArrayList<String> getCanonicalNames( Class[] classCollection ) {
		
		ArrayList<String> result = new ArrayList<String>();
		
		for (Class c : classCollection) {
			result.add( c.getCanonicalName() );
		}
		return result;
		
	}


	
}
