package eu.linqed.debugtoolbar;

/*
 * <<
 * XPage Debug Toolbar
 * Copyright 2012,2013 Mark Leusink http://linqed.eu
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

import java.io.Serializable;
import java.util.Date;
import java.util.Vector;

import javax.faces.context.FacesContext;

import lotus.domino.NotesException;

import com.ibm.xsp.designer.context.XSPContext;

public class Message implements Serializable {

	private static final long serialVersionUID = -4017804868101698470L;
	
	public static final String TYPE_INFO = "info";
	public static final String TYPE_WARNING = "warning";
	public static final String TYPE_DEBUG = "debug";
	public static final String TYPE_ERROR = "error";
	public static  final String TYPE_DIVIDER = "divider";
	
	private String text;
	private String details;
	private String summary;
	
	private String type;
	private Date date;
	private String context;
	private String fromPage;
	
	private int errorId = 0;
	private int errorLine = 0;
	private String errorClassName;
	private String errorMethod;

	private Vector<String> stackTrace;
	
	private boolean showStackTrace = false;
	
	public Message(Object obj, String context, String type) {
		this.text = getMessageText(obj);
		this.details = this.text;
		this.type = (type==null ? TYPE_INFO : type);
		this.context = context;
		this.date = new Date();
	}
	
	public Message(Throwable error, String context) {
		
		StringBuilder sbHTML = new StringBuilder();
		StringBuilder sbDetails = new StringBuilder();
		
		if (error instanceof NotesException) {		//add error id
			errorId = ((NotesException) error).id;
			summary = ((NotesException) error).text;
			sbHTML.append( summary + " (" + errorId + ")");
		} else {
			summary = error.toString();
			sbHTML.append( error.toString() );
		}
		sbDetails.append( summary );
		
		StackTraceElement[] stes = error.getStackTrace();
		errorLine = stes[0].getLineNumber();
		errorMethod = stes[0].getMethodName();
		errorClassName = stes[0].getClassName();
		
		if (error.getClass().getName().equals( "javax.faces.FacesException") ) {
			
			sbHTML.append("<table cellpadding=\"0\" cellspacing=\"0\" class=\"errorDetails\"><tbody><tr><td colspan=\"2\"><b><u>Error details</u></b></td></tr>");
			sbDetails.append("\n\nError details\n\n");
		
			javax.faces.FacesException fE = (javax.faces.FacesException) error;
			
			if ( fE.getCause().getClass().getName().equals( "com.ibm.xsp.exception.EvaluationExceptionEx") ) {
				
				com.ibm.xsp.exception.EvaluationExceptionEx evEx = (com.ibm.xsp.exception.EvaluationExceptionEx) fE.getCause();
				
				if ( evEx.getCause().getClass().getName().equals("com.ibm.jscript.InterpretException") ) {
					
					com.ibm.jscript.InterpretException iEx = (com.ibm.jscript.InterpretException) evEx.getCause();
					
					sbHTML.append("<tr><td><b>Message:</b></td><td style=\"color: black;\">" + iEx.getMessage() + "</td></tr>");
					sbHTML.append("<tr><td><b>JavaScript code:</b></td><td style=\"color: black;\">" + iEx.getExpressionText().replace("\n", "<br />") + "</td></tr>");
					
					sbDetails.append("Message: " + iEx.getMessage() + "\n\n" );
					sbDetails.append("JavaScript code:\n\n" + iEx.getExpressionText() + "\n\n");
					
				}
				
				sbHTML.append("<tr><td><b>Caused by component:</b></td><td style=\"color: black;\">" + evEx.getErrorComponentId() + "</td></tr>");
				sbHTML.append("<tr><td><b>Property:</b></td><td style=\"color: black;\">" + evEx.getErrorPropertyId() + "</td></tr>" );
				
				sbDetails.append("Caused by component: " + evEx.getErrorComponentId() + "\n");
				sbDetails.append("Property: " + evEx.getErrorPropertyId() + "\n" );
			}
			
			XSPContext xspContext = XSPContext.getXSPContext(FacesContext.getCurrentInstance());
			String [] historyUrls = xspContext.getHistoryUrls();
			fromPage = historyUrls[historyUrls.length-1];
			
			if (fromPage.indexOf("?")>-1) {
				fromPage = fromPage.substring(0, fromPage.indexOf("?"));
			}
			if (fromPage.indexOf("/")==0) {
				fromPage = fromPage.substring(1);
			}
			
			sbHTML.append("<tr><td><b>In page:</b></td><td><a href=\"" + FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/" + fromPage + "\">" + fromPage + "</a></td></tr>");
			sbHTML.append("</tbody></table>");
			
			sbDetails.append("In page: " + fromPage);
		}
		
		this.details = sbDetails.toString();
		this.text = sbHTML.toString();
		this.type = TYPE_ERROR;
		this.context = context;
		this.date = new Date();
		
		//store stacktrace elements in a Vector
		stackTrace = new Vector<String>();
		for (StackTraceElement element : error.getStackTrace()) {
			stackTrace.add( element.toString() );
		}
	}
	
	public String getFromPage() {
		return this.fromPage;
	}
	
	private static String getMessageText(Object obj) {
		
		String text = "";
		
		try {
			text = (String) obj;		//strings
		} catch (ClassCastException cce) {
			
			try {
				text = obj.toString();
			} catch ( Exception e) {
				text = "error: cannot retrieve text for object of class " + obj.getClass().getName();
			}
			
		} catch (Exception e) {
			text = "error: cannot retrieve text for object of class " + obj.getClass().getName();
		}
		
		return text;
	}
	
	public boolean hasStackTrace() {
		return (this.stackTrace != null && this.stackTrace.size()>0);
	}
	
	public boolean isShowStackTrace() {
		return showStackTrace;
	}
	public void setShowStackTrace(boolean show) {
		this.showStackTrace = show;
	}
	
	public Vector<String> getStackTrace() {
		return stackTrace;
	}
	
	public String getText() {
		return text;
	}
	
	public int getErrorLine() {
		return errorLine;
	}
	public String getErrorClassName() {
		return errorClassName;
	}
	public String getErrorMethod() {
		return errorMethod;
	}
	
	public String getSummary() {
		return summary;
	}
	public String getDetails() {
		return details;
	}
	public int getErrorId() {
		return errorId;
	}

	public String getType() {
		return type;
	}

	public String getContext() {
		return context;
	}

	public Date getDate() {
		return date;
	}
	
	public String getStyleclass() {
		if (type.equals(TYPE_ERROR) || type.equals(TYPE_WARNING) || type.equals(TYPE_DEBUG)) {
			return type;
		} else {
			return "";
		}
	}

}

