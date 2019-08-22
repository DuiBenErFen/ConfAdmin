package com.conf.admin.controller;

import com.conf.admin.controller.annotation.PermessionLimit;
import com.conf.admin.core.model.ConfProject;
import com.conf.admin.core.util.ReturnT;
import com.conf.admin.dao.ConfNodeDao;
import com.conf.admin.dao.ConfProjectDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.List;

/**
 * 项目管理
 */
@Controller
@RequestMapping("/project")
public class ProjectController {
	
	@Resource
	private ConfProjectDao confProjectDao;
	@Resource
	private ConfNodeDao confNodeDao;

	@RequestMapping
	@PermessionLimit(adminuser = true)
	public String index(Model model) {

		List<ConfProject> list = confProjectDao.findAll();
		model.addAttribute("list", list);

		return "project/project.index";
	}

	@RequestMapping("/save")
	@PermessionLimit(adminuser = true)
	@ResponseBody
	public ReturnT<String> save(ConfProject confProject){

		// valid
		if (StringUtils.isBlank(confProject.getAppname())) {
			return new ReturnT<String>(500, "AppName不可为空");
		}
		if (confProject.getAppname().length()<4 || confProject.getAppname().length()>100) {
			return new ReturnT<String>(500, "Appname长度限制为4~100");
		}
		if (StringUtils.isBlank(confProject.getTitle())) {
			return new ReturnT<String>(500, "请输入项目名称");
		}

		// valid repeat
		ConfProject existProject = confProjectDao.load(confProject.getAppname());
		if (existProject != null) {
			return new ReturnT<String>(500, "Appname已存在，请勿重复添加");
		}

		int ret = confProjectDao.save(confProject);
		return (ret>0)?ReturnT.SUCCESS:ReturnT.FAIL;
	}

	@RequestMapping("/update")
	@PermessionLimit(adminuser = true)
	@ResponseBody
	public ReturnT<String> update(ConfProject confProject){

		// valid
		if (StringUtils.isBlank(confProject.getAppname())) {
			return new ReturnT<String>(500, "AppName不可为空");
		}
		if (StringUtils.isBlank(confProject.getTitle())) {
			return new ReturnT<String>(500, "请输入项目名称");
		}

		int ret = confProjectDao.update(confProject);
		return (ret>0)?ReturnT.SUCCESS:ReturnT.FAIL;
	}

	@RequestMapping("/remove")
	@PermessionLimit(adminuser = true)
	@ResponseBody
	public ReturnT<String> remove(String appname){

		if (StringUtils.isBlank(appname)) {
			return new ReturnT<String>(500, "参数AppName非法");
		}

		// valid
		int list_count = confNodeDao.pageListCount(0, 10, null, appname, null);
		if (list_count > 0) {
			return new ReturnT<String>(500, "拒绝删除，该项目下存在配置数据");
		}

		List<ConfProject> allList = confProjectDao.findAll();
		if (allList.size() == 1) {
			return new ReturnT<String>(500, "拒绝删除, 需要至少预留一个项目");
		}

		int ret = confProjectDao.delete(appname);
		return (ret>0)?ReturnT.SUCCESS:ReturnT.FAIL;
	}

}
