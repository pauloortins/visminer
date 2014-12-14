package org.visminer.metric;

import java.io.IOException;
import java.util.ArrayList;

public class SoftwareType {
	
	private String name;
	private ArrayList<SoftwareMethod> methods;
	
	public SoftwareType() {
		methods = new ArrayList<SoftwareMethod>();
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public ArrayList<SoftwareMethod> getMethods() {
		return methods;
	}
	
	public void addMethod(SoftwareMethod method) {
		method.setName(name + "." + method.getName());
		this.methods.add(method);
	}		
}
