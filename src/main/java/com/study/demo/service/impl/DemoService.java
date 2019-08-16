package com.study.demo.service.impl;

import com.study.demo.service.IDemoService;
import com.study.mvcframework.annotation.MyService;

/**
 * 核心业务逻辑
 */
@MyService
public class DemoService implements IDemoService {

	public DemoService() {}

	public String get(String name) {
		return "My name is " + name;
	}

}
