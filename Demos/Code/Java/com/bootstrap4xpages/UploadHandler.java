package com.bootstrap4xpages;

import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Map;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.json.JsonGenerator;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.xsp.designer.context.XSPContext;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.http.UploadedFile;
import com.ibm.xsp.webapp.XspHttpServletResponse;

public class UploadHandler implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String fileName;
	private long fileSize;
	private String targetUnid;
	private transient Database dbCurrent;
	
	public UploadHandler() {
		
	}
	
	public void go() {
		
		XspHttpServletResponse response=null;
		PrintWriter pw = null;
		FacesContext facesContext = FacesContext.getCurrentInstance();
		
		try {
			
			System.out.println("go !");
			
			//XSPContext xspContext = XSPContext.getXSPContext(facesContext);

			ExternalContext extCon = facesContext.getExternalContext();
			
			response =  (XspHttpServletResponse) extCon.getResponse();
			pw = response.getWriter();
			
			dbCurrent = ExtLibUtil.getCurrentDatabase();
			
			//send required plupload response
			response.setContentType("text/json");
			response.setHeader("Cache-Control", "no-cache");		//no caching
			response.setDateHeader("Expires", -1);
		 

			//only HTTP POST is allowed
			HttpServletRequest request = (HttpServletRequest) extCon.getRequest();
			
			//System.out.println("method: " + request.getMethod() );
			
			if (!request.getMethod().equalsIgnoreCase("post")) {
				
				//response.sendError(405, "only POST is allowed (and this was a " + request.getMethod() + ")");
				pw.println("sorry, only post");
				
				return;
			}
			
			String key = "files[]";
			
			//check if a file was uploaded
			Map map = request.getParameterMap();
			if (!map.containsKey( key )) {
				return;		
			}
			
			UploadedFile uploadedFile = (UploadedFile) map.get( key );
			
			if ( !storeUploadedFile(uploadedFile) ) {
				
				System.out.println("could not save file...");
				return;
				
			}
			
			//response should be:
			
			/*
			 * {"files": [
  {
    "name": "picture1.jpg",
    "size": 902604,
    "url": "http:\/\/example.org\/files\/picture1.jpg",
    "thumbnail_url": "http:\/\/example.org\/files\/thumbnail\/picture1.jpg",
    "delete_url": "http:\/\/example.org\/files\/picture1.jpg",
    "delete_type": "DELETE"
  },
  {
    "name": "picture2.jpg",
    "size": 841946,
    "url": "http:\/\/example.org\/files\/picture2.jpg",
    "thumbnail_url": "http:\/\/example.org\/files\/thumbnail\/picture2.jpg",
    "delete_url": "http:\/\/example.org\/files\/picture2.jpg",
    "delete_type": "DELETE"
  }
]}
			 */
			
			
			JsonJavaFactory factory = JsonJavaFactory.instanceEx;
			
			//JsonJavaArray jsonArray = new JsonJavaArray();
			
				
				JsonJavaObject jsonFileObject = new JsonJavaObject();
				jsonFileObject.putString("name", fileName );
				jsonFileObject.putLong("size", fileSize);
				
				String url = "/" + dbCurrent.getFilePath().replace("\\", "/") + "/0/" + targetUnid + "/$file/" + java.net.URLEncoder.encode(fileName, "UTF-8");
				jsonFileObject.putString("url", url );
				jsonFileObject.putString("thumbnail_url", url );
				jsonFileObject.putString("delete_url", url );
				jsonFileObject.putString("delete_type", "DELETE" );
				
				//jsonArray.putObject(0, jsonFileObject);
				
				//JsonJavaObject jsonObject = new JsonJavaObject();
				//jsonObject.putArray("files", jsonArray);
				
				pw.print( "{ \"files\" : [" + jsonFileObject.toString() + "]}");
			
			response.commitResponse();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			facesContext.responseComplete();
		}
	}

	
	private boolean storeUploadedFile( UploadedFile uploadedFile) {
		
		File correctedFile = null;
		RichTextItem rtFiles = null;
		Document doc = null;
		
		boolean success = false;
		
		try {
			
			if (uploadedFile==null) {
				return success;
			}
			
			System.out.println(uploadedFile.getServerFile());
			
			doc = dbCurrent.createDocument();
			doc.replaceItemValue("form", "upload");
					
			
			String ITEM_NAME_FILES = "file";
			
			///File f = uploadedFile.getServerFile();
			//File[] fArray = uploadedFile.getServerFiles();
			
		
			//get uploaded file and attach it to the document
			fileName = uploadedFile.getClientFileName();
			
		 	File tempFile = uploadedFile.getServerFile();		//the uploaded file with a cryptic name
		 	
		 	//name = fileName;
		 	//nameEncoded = Utils.encodeURLParameter(name);
		 	fileSize = tempFile.length();
		 	targetUnid = doc.getUniversalID();
		 	
		 	correctedFile = new java.io.File( tempFile.getParentFile().getAbsolutePath() + java.io.File.separator + fileName );
		 	
		 	//rename the file on the OS so we can embed it with its correct name
		 	@SuppressWarnings("unused")
			boolean renameSuccess = tempFile.renameTo(correctedFile);
		 	
		 	//embed original file in target document
		 	rtFiles = doc.createRichTextItem(ITEM_NAME_FILES);
		 	rtFiles.embedObject(lotus.domino.EmbeddedObject.EMBED_ATTACHMENT, "", correctedFile.getAbsolutePath(), null);

		 	success = doc.save();
		 	
		} catch (Exception e) { 

			e.printStackTrace();
			
		} finally {
			
			recycle(rtFiles, doc);
			
			try {
				if (correctedFile != null) {
					//rename the temporary file back to its original name so it's automatically
				 	//removed from the os' file system.
				 	correctedFile.renameTo(uploadedFile.getServerFile());	
				}
			} catch(Exception ee) { ee.printStackTrace(); }
			
		}
		
		return success;

	}
	
	//recycles all objects in the argeuments
	private static void recycle (Object... dominoObjects) {
		
	    for (Object dominoObject : dominoObjects) {
	    	if ( dominoObject != null ) {
	            if (dominoObject instanceof Base) {
	                try {
	                	( (Base) dominoObject) .recycle();
	                } catch (NotesException ne) { }
	            }
	        }
	    }
	    
	}
}
