package com.conf.admin.controller;

import com.conf.admin.controller.annotation.PermessionLimit;
import com.conf.admin.core.model.ConfEnv;
import com.conf.admin.dao.ConfEnvDao;
import com.conf.admin.dao.ConfNodeDao;
import com.conf.admin.core.util.ReturnT;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.List;

/**
 * 环境管理
 */
@Controller
@RequestMapping("/env")
public class EnvController {
	
	@Resource
	private ConfEnvDao confEnvDao;
    @Resource
    private ConfNodeDao confNodeDao;


	@RequestMapping
	@PermessionLimit(adminuser = true)
	public String index(Model model) {

		List<ConfEnv> list = confEnvDao.findAll();
		model.addAttribute("list", list);

		return "env/env.index";
	}

	@RequestMapping("/save")
	@PermessionLimit(adminuser = true)
	@ResponseBody
	public ReturnT<String> save(ConfEnv confEnv){

		// valid
		if (StringUtils.isBlank(confEnv.getEnv())) {
			return new ReturnT<String>(500, "Env不可为空");
		}
		if (confEnv.getEnv().length()<3 || confEnv.getEnv().length()>50) {
			return new ReturnT<String>(500, "Env长度限制为4~50");
		}
		if (StringUtils.isBlank(confEnv.getTitle())) {
			return new ReturnT<String>(500, "请输入Env名称");
		}

		// valid repeat
		ConfEnv existEnv = confEnvDao.load(confEnv.getEnv());
		if (existEnv != null) {
			return new ReturnT<String>(500, "Env已存在，请勿重复添加");
		}

		int ret = confEnvDao.save(confEnv);
		return (ret>0)?ReturnT.SUCCESS:ReturnT.FAIL;
	}

	@RequestMapping("/update")
	@PermessionLimit(adminuser = true)
	@ResponseBody
	public ReturnT<String> update(ConfEnv confEnv){

		// valid
		if (StringUtils.isBlank(confEnv.getEnv())) {
			return new ReturnT<String>(500, "Env不可为空");
		}
		if (StringUtils.isBlank(confEnv.getTitle())) {
			return new ReturnT<String>(500, "请输入Env名称");
		}

		int ret = confEnvDao.update(confEnv);
		return (ret>0)?ReturnT.SUCCESS:ReturnT.FAIL;
	}

	@RequestMapping("/remove")
	@PermessionLimit(adminuser = true)
	@ResponseBody
	public ReturnT<String> remove(String env){

		if (StringUtils.isBlank(env)) {
			return new ReturnT<String>(500, "参数Env非法");
		}

        // valid
        int list_count = confNodeDao.pageListCount(0, 10, env, null, null);
        if (list_count > 0) {
            return new ReturnT<String>(500, "拒绝删除，该Env下存在配置数据");
        }

		// valid can not be empty
		List<ConfEnv> allList = confEnvDao.findAll();
		if (allList.size() == 1) {
			return new ReturnT<String>(500, "拒绝删除, 需要至少预留一个Env");
		}

		int ret = confEnvDao.delete(env);
		return (ret>0)?ReturnT.SUCCESS:ReturnT.FAIL;
	}

}
