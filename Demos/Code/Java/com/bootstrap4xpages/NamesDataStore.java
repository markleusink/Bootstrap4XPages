package com.bootstrap4xpages;

import java.io.Serializable;
import java.util.ArrayList;

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.xsp.extlib.util.ExtLibUtil;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

import java.util.Collection;
import java.util.Iterator;

import lotus.domino.Document;
import lotus.domino.DocumentCollection;

import com.ibm.commons.util.io.json.JsonGenerator;
import com.ibm.commons.util.io.json.JsonJavaFactory;
	
	public class NamesDataStore implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private String dbNamesPath = "bs4xp\\fakenames.nsf";
		
		private transient Database dbDirectory = null;
		
		public String findUsers( String query ) {
			
			DocumentCollection dc = null;
			
			if (query == null || query.length()==0 ) {
				return null;
			}
			
			ArrayList<String> data = new ArrayList<String>();
			String result = null;
			
			int MAX_RESULTS = 50;
			
			try {
				
				dbDirectory = getDirectoryDb();
				
				String searchQuery = "[Form]=\"Person\" AND ([FirstName]=\"" + query + "*\" OR [LastName]=\"" + query + "*\" OR [InternetAddress]=\"" + query + "*\")";
				dc = dbDirectory.FTSearch(searchQuery, 50);
				
				int counter = 0;
				
				JsonJavaFactory factory = JsonJavaFactory.instanceEx;
				
				Document doc = dc.getFirstDocument();
				
				boolean hasMore = false;
				
				while (doc != null) {
					
					String fullName = doc.getItemValueString("fullName");
					
					JsonJavaObject jsonObject = new JsonJavaObject();
					jsonObject.putString("id", fullName );
					jsonObject.putString("text", fullName.substring(0, fullName.indexOf("/")) );
				
					data.add( JsonGenerator.toJson(factory, jsonObject) );
				
					counter++;
					
					Document tmp = dc.getNextDocument(doc);
					
					if (counter >= MAX_RESULTS) {		
						hasMore = true;
						tmp = null; 
					}
					
					doc.recycle();
					doc = tmp;
				}
				
				result = "{\"hasMore\" : " + (hasMore ? "true" : "false") + ", \"results\": [" + NamesDataStore.join(data, ",") + "]}";			
				
			} catch (Exception e) {
				
				e.printStackTrace();
					
				result = "{\"hasMore\" : false, \"results\": [ {\"text\":\"Er is een fout opgetreden\", \"id\":\"\"} ]}";
				
			} finally {
				
				if (dc != null) { try {
					dc.recycle();
				} catch (NotesException e) {
					e.printStackTrace();
				} }

			}
			
			return result;

		}
		
		public String getUser( String userName ) {
			
			View vwUsers = null;
			
			if (userName == null || userName.length()==0 ) {
				return null;
			}
			
			ArrayList<String> data = new ArrayList<String>();
			String result = null;
			
			try {
				
				dbDirectory = getDirectoryDb();
				vwUsers = dbDirectory.getView("vwAllByFullName");
				Document doc = vwUsers.getDocumentByKey(userName, true);
				
				if (doc != null) {
				
					JsonJavaFactory factory = JsonJavaFactory.instanceEx;
				
					String fullName = doc.getItemValueString("fullName");
					
					JsonJavaObject jsonObject = new JsonJavaObject();
					jsonObject.putString("id", fullName );
					jsonObject.putString("text", fullName.substring(0, fullName.indexOf("/")) );
				
					data.add( JsonGenerator.toJson(factory, jsonObject) );
					
					doc.recycle();
					
					result = NamesDataStore.join(data, ",");
				} else {
					
					result = "{id:\"\", text:\"User " + userName + " not found\"}";
				}
				
			} catch (Exception e) {
				
				e.printStackTrace();
					
				result = "{id:\"\", text:\"An error occurred\"}";
				
			} finally {
				
				if (vwUsers != null) { try {
					vwUsers.recycle();
				} catch (NotesException e) {
					e.printStackTrace();
				} }

			}
			
			return result;

		}
		
		private Database getDirectoryDb() throws NotesException {
			
			if (dbDirectory==null ) {
				
				Session session = ExtLibUtil.getCurrentSession();
					
				dbDirectory = session.getDatabase("", dbNamesPath );
				
			}
			
			return dbDirectory;
			
		}
		
		@SuppressWarnings("unchecked")
		private static String join(Collection<?> s, String delimiter) {
			StringBuilder builder = new StringBuilder();
			Iterator iter = s.iterator();

			while (iter.hasNext()) {
				builder.append(iter.next());
				if (!iter.hasNext()) {
					break;
				}
				builder.append(delimiter);
			}
			return builder.toString();
		}
		
	}

