package com.conf.admin.controller;

import com.conf.admin.controller.annotation.PermessionLimit;
import com.conf.admin.controller.interceptor.EnvInterceptor;
import com.conf.admin.core.model.ConfNode;
import com.conf.admin.core.model.ConfProject;
import com.conf.admin.core.model.ConfUser;
import com.conf.admin.core.util.JacksonUtil;
import com.conf.admin.core.util.ReturnT;
import com.conf.admin.dao.ConfProjectDao;
import com.conf.admin.service.ConfNodeService;
import com.conf.admin.service.impl.LoginService;
import com.xxl.conf.core.model.XxlConfParamVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 配置管理
 */
@Controller
@RequestMapping("/conf")
public class ConfController {

	@Resource
	private ConfProjectDao confProjectDao;
	@Resource
	private ConfNodeService confNodeService;

	/**
	 *选择appname，根据appname罗列出对应的配置key
	 */
	@RequestMapping("")
	public String index(HttpServletRequest request, Model model, String appname){

		List<ConfProject> list = confProjectDao.findAll();
		if (list==null || list.size()==0) {
			throw new RuntimeException("系统异常，无可用项目");
		}

		ConfProject project = list.get(0);
		for (ConfProject item: list) {
			if (item.getAppname().equals(appname)) {
				project = item;
			}
		}

		boolean ifHasProjectPermission = confNodeService.ifHasProjectPermission(
				(ConfUser) request.getAttribute(LoginService.LOGIN_IDENTITY),
				(String) request.getAttribute(EnvInterceptor.CURRENT_ENV),
				project.getAppname());

		model.addAttribute("ProjectList", list);
		model.addAttribute("project", project);
		model.addAttribute("ifHasProjectPermission", ifHasProjectPermission);

		return "conf/conf.index";
	}

	/**
	 *每页罗列的配置key条数，默认10条一页，搜索也是走这里
	 */
	@RequestMapping("/pageList")
	@ResponseBody
	public Map<String, Object> pageList(HttpServletRequest request,
										@RequestParam(required = false, defaultValue = "0") int start,
										@RequestParam(required = false, defaultValue = "10") int length,
										String appname,
										String key) {

		ConfUser confUser = (ConfUser) request.getAttribute(LoginService.LOGIN_IDENTITY);
		String loginEnv = (String) request.getAttribute(EnvInterceptor.CURRENT_ENV);

		return confNodeService.pageList(start, length, appname, key, confUser, loginEnv);
	}

	/**
	 * get
	 * @return
	 */
	@RequestMapping("/delete")
	@ResponseBody
	public ReturnT<String> delete(HttpServletRequest request, String key){

		ConfUser confUser = (ConfUser) request.getAttribute(LoginService.LOGIN_IDENTITY);
		String loginEnv = (String) request.getAttribute(EnvInterceptor.CURRENT_ENV);

		return confNodeService.delete(key, confUser, loginEnv);
	}

	/**
	 * create/update
	 * @return
	 */
	@RequestMapping("/add")
	@ResponseBody
	public ReturnT<String> add(HttpServletRequest request, ConfNode confNode){

		ConfUser confUser = (ConfUser) request.getAttribute(LoginService.LOGIN_IDENTITY);
		String loginEnv = (String) request.getAttribute(EnvInterceptor.CURRENT_ENV);

		// fill env
		confNode.setEnv(loginEnv);

		return confNodeService.add(confNode, confUser, loginEnv);
	}
	
	/**
	 * create/update
	 * @return
	 */
	@RequestMapping("/update")
	@ResponseBody
	public ReturnT<String> update(HttpServletRequest request, ConfNode confNode){

		ConfUser confUser = (ConfUser) request.getAttribute(LoginService.LOGIN_IDENTITY);
		String loginEnv = (String) request.getAttribute(EnvInterceptor.CURRENT_ENV);

		// fill env
		confNode.setEnv(loginEnv);

		return confNodeService.update(confNode, confUser, loginEnv);
	}

	// ---------------------- rest api ----------------------

    @Value("${xxl.conf.access.token}")
    private String accessToken;

	@RequestMapping("/find")
	@ResponseBody
	@PermessionLimit(limit = false)
	public ReturnT<Map<String, String>> find(@RequestBody(required = false) String data){
		System.out.println("进入/fine");
		System.out.println(data);
		// parse data
		XxlConfParamVO confParamVO = null;
		try {
			confParamVO = (XxlConfParamVO) JacksonUtil.readValue(data, XxlConfParamVO.class);
		} catch (Exception e) { }

		// parse param
		String accessToken = null;
		String env = null;
		List<String> keys = null;
		if (confParamVO != null) {
			accessToken = confParamVO.getAccessToken();
			env = confParamVO.getEnv();
			keys = confParamVO.getKeys();
		}

		return confNodeService.find(accessToken, env, keys);
	}

	@RequestMapping("/monitor")
	@ResponseBody
	@PermessionLimit(limit = false)
	public DeferredResult<ReturnT<String>> monitor(@RequestBody(required = false) String data){

		// parse data
		XxlConfParamVO confParamVO = null;
		try {
			confParamVO = (XxlConfParamVO) JacksonUtil.readValue(data, XxlConfParamVO.class);
		} catch (Exception e) { }

		// parse param
		String accessToken = null;
		String env = null;
		List<String> keys = null;
		if (confParamVO != null) {
			accessToken = confParamVO.getAccessToken();
			env = confParamVO.getEnv();
			keys = confParamVO.getKeys();
		}

		return confNodeService.monitor(accessToken, env, keys);
	}


}
