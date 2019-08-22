package com.conf.admin.service;


import com.conf.admin.core.model.ConfNode;
import com.conf.admin.core.model.ConfUser;
import com.conf.admin.core.util.ReturnT;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.Map;

public interface ConfNodeService {

	public boolean ifHasProjectPermission(ConfUser loginUser, String loginEnv, String appname);

	public Map<String,Object> pageList(int offset,
									   int pagesize,
									   String appname,
									   String key,
									   ConfUser loginUser,
									   String loginEnv);

	public ReturnT<String> delete(String key, ConfUser loginUser, String loginEnv);

	public ReturnT<String> add(ConfNode confNode, ConfUser loginUser, String loginEnv);

	public ReturnT<String> update(ConfNode confNode, ConfUser loginUser, String loginEnv);

    /*ReturnT<String> syncConf(String appname, ConfUser loginUser, String loginEnv);*/


    // ---------------------- rest api ----------------------

    public ReturnT<Map<String, String>> find(String accessToken, String env, List<String> keys);

    public DeferredResult<ReturnT<String>> monitor(String accessToken, String env, List<String> keys);

}
