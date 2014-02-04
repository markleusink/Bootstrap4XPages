package eu.linqed.debugtoolbar;

import java.io.Serializable;

import lotus.domino.Document;

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

/*
 * Class that shows how you can use the XPage Debug Toolbar from Java 
 */
public class DemoClass implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static final DebugToolbar dBar = DebugToolbar.get();
	
	public DemoClass() {
	
	}
	
	public void someMethod() {
		
		dBar.info("message from a bean to the toolbar", "DemoClass.someMethod");
		dBar.warn("warning message from a bean to the toolbar", "DemoClass.someMethod");
		
		otherMethod();

	}

	public void otherMethod() {
		
		dBar.debug("debug message", "DemoClass.otherMethod");
		
	}
	
	@SuppressWarnings("null")
	public void faultyMethod() {
	
		Document doc = null;
		
		try {
			
			@SuppressWarnings("unused")
			String id = doc.getUniversalID();		//will throw a NullPointerException
			
		} catch (Exception e) {
			
			//if you're only using the toolbar in a class to log errors,
			//you might want to call ik like this, so no object is created
			//if no error occur
			DebugToolbar.get().error(e, "DemoClass.faultyMethod");

		}
		
	}
	
	
	
}
