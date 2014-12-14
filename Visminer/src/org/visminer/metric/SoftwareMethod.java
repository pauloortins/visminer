package org.visminer.metric;

import java.util.ArrayList;

public class SoftwareMethod {
	private String name;	
	private ArrayList<String> calledMethods;
	
	public SoftwareMethod() {
		calledMethods = new ArrayList<String>();
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public ArrayList<String> getCalledsMethods() {
		return calledMethods;
	}
	
	public void addCalledMethod(String calledMethodName) {
		calledMethods.add(calledMethodName);
	}
}
