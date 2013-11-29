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

import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.ibm.xsp.http.UploadedFile;
import com.ibm.xsp.webapp.XspHttpServletResponse;

public class UploadHandler implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final String FORM_NAME = "upload";
	private static final String FIELD_NAME = "file";
	private static final String REQ_FILE_PARAM = "files[]";
	private static long MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024;		//1 MB

	private String fileName;
	private long fileSize;
	private String targetUnid;
	private transient Database dbCurrent;

	public UploadHandler() {

	}

	@SuppressWarnings("unchecked")
	public void go() {

		XspHttpServletResponse response = null;
		PrintWriter pw = null;
		FacesContext facesContext = FacesContext.getCurrentInstance();

		try {

			ExternalContext extCon = facesContext.getExternalContext();

			response = (XspHttpServletResponse) extCon.getResponse();
			pw = response.getWriter();

			dbCurrent = ExtLibUtil.getCurrentDatabase();

			// send required plupload response
			response.setContentType("text/json");
			response.setHeader("Cache-Control", "no-cache"); // no caching
			response.setDateHeader("Expires", -1);

			// we only allow a HTTP POST
			HttpServletRequest request = (HttpServletRequest) extCon
					.getRequest();

			if (!request.getMethod().equalsIgnoreCase("post")) {
				throw( new Exception("only POST is allowed") );
			}

			// check if a file was uploaded and if the parameter name is correct
			Map map = request.getParameterMap();
			if (!map.containsKey(REQ_FILE_PARAM)) {
				throw( new Exception("invalid input") );
			}

			UploadedFile uploadedFile = (UploadedFile) map.get(REQ_FILE_PARAM);
			
			if ( uploadedFile.getServerFile().length() > MAX_FILE_SIZE_BYTES ) {
				throw( new Exception("file is too large"));
			}

			if (!storeUploadedFile(uploadedFile)) {
				throw( new Exception("file could not be saved") );
			}

			// set up JSON response
			JsonJavaObject jsonFileObject = new JsonJavaObject();
			jsonFileObject.putString("name", fileName);
			jsonFileObject.putLong("size", fileSize);

			String url = "/" + dbCurrent.getFilePath().replace("\\", "/")
					+ "/0/" + targetUnid + "/$file/"
					+ java.net.URLEncoder.encode(fileName, "UTF-8");
			jsonFileObject.putString("url", url);
			jsonFileObject.putString("thumbnailUrl", url);
			//jsonFileObject.putString("delete_url", url);
			//jsonFileObject.putString("delete_type", "DELETE");

			pw.print("{ \"files\" : [" + jsonFileObject.toString() + "]}");

			response.commitResponse();

		} catch (Exception e) {

			if (pw != null) {
				pw.print("{ \"files\" : [], \"error\" : true, \"errorMsg\" : \""
								+ e.getMessage() + "\"}");
			} else {
				e.printStackTrace();
			}
		} finally {
			facesContext.responseComplete();
		}
	}

	private boolean storeUploadedFile(UploadedFile uploadedFile) {

		File correctedFile = null;
		RichTextItem rtFiles = null;
		Document doc = null;

		boolean success = false;

		try {

			if (uploadedFile == null) {
				return success;
			}

			System.out.println(uploadedFile.getServerFile());

			doc = dbCurrent.createDocument();
			doc.replaceItemValue("form", FORM_NAME);

			// get uploaded file and attach it to the document
			fileName = uploadedFile.getClientFileName();

			File tempFile = uploadedFile.getServerFile(); // the uploaded file
															// with a cryptic
															// name

			// name = fileName;
			// nameEncoded = Utils.encodeURLParameter(name);
			fileSize = tempFile.length();
			targetUnid = doc.getUniversalID();

			correctedFile = new java.io.File(tempFile.getParentFile()
					.getAbsolutePath()
					+ java.io.File.separator + fileName);

			// rename the file on the OS so we can embed it with its correct
			// name
			@SuppressWarnings("unused")
			boolean renameSuccess = tempFile.renameTo(correctedFile);

			// embed original file in target document
			rtFiles = doc.createRichTextItem(FIELD_NAME);
			rtFiles.embedObject(lotus.domino.EmbeddedObject.EMBED_ATTACHMENT,
					"", correctedFile.getAbsolutePath(), null);

			success = doc.save();

		} catch (Exception e) {

			e.printStackTrace();

		} finally {

			recycle(rtFiles, doc);

			try {
				if (correctedFile != null) {
					// rename the temporary file back to its original name so
					// it's automatically
					// removed from the os' file system.
					correctedFile.renameTo(uploadedFile.getServerFile());
				}
			} catch (Exception ee) {
				ee.printStackTrace();
			}

		}

		return success;

	}

	// recycles all objects in the argeuments
	private static void recycle(Object... dominoObjects) {

		for (Object dominoObject : dominoObjects) {
			if (dominoObject != null) {
				if (dominoObject instanceof Base) {
					try {
						((Base) dominoObject).recycle();
					} catch (NotesException ne) {
					}
				}
			}
		}

	}
}
