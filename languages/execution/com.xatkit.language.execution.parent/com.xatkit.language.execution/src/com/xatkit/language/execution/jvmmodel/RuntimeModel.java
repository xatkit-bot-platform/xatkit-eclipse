package com.xatkit.language.execution.jvmmodel;

import java.util.Collections;
import java.util.Map;

public class RuntimeModel {
	
	protected Map<String, String> context;
	
	protected Map<String, Object> session;
	
	public RuntimeModel() {
		this(Collections.emptyMap(), Collections.emptyMap());
	}
	
	public RuntimeModel(Map<String, String> context, Map<String, Object> sesssion) {
		this.context = context;
		this.session = sesssion;
	}

}
