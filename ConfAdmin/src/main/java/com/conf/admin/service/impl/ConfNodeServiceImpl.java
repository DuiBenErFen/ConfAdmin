package com.conf.admin.service.impl;

import com.conf.admin.core.model.*;
import com.conf.admin.dao.*;
import com.conf.admin.service.ConfNodeService;
import com.conf.admin.core.util.RegexUtil;
import com.conf.admin.core.util.ReturnT;
import com.xxl.conf.core.util.PropUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import javax.annotation.Resource;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 配置
 */
@Service
public class ConfNodeServiceImpl implements ConfNodeService, InitializingBean, DisposableBean {
	private static Logger logger = LoggerFactory.getLogger(ConfNodeServiceImpl.class);


	@Resource
	private ConfNodeDao confNodeDao;
	@Resource
	private ConfProjectDao confProjectDao;

	@Resource
	private ConfNodeLogDao confNodeLogDao;
	@Resource
	private ConfEnvDao confEnvDao;
	@Resource
	private ConfNodeMsgDao confNodeMsgDao;


	@Value("${xxl.conf.confdata.filepath}")
	private String confDataFilePath;
	@Value("${xxl.conf.access.token}")
	private String accessToken;

	private int confBeatTime = 30;

	/**
	 *判断用户有无对应项目的配置权限
	 */
	@Override
	public boolean ifHasProjectPermission(ConfUser loginUser, String loginEnv, String appname){
		if (loginUser.getPermission() == 1) {
			return true;
		}
		if (ArrayUtils.contains(StringUtils.split(loginUser.getPermissionData(), ","), (appname.concat("#").concat(loginEnv)) )) {
			return true;
		}
		return false;
	}

	/**
	 *列出用户拥有的配置权限对应的配置key
	 */
	@Override
	public Map<String,Object> pageList(int offset,
									   int pagesize,
									   String appname,
									   String key,
									   ConfUser loginUser,
									   String loginEnv) {

		// project permission
		if (StringUtils.isBlank(loginEnv) || StringUtils.isBlank(appname) || !ifHasProjectPermission(loginUser, loginEnv, appname)) {
			//return new ReturnT<String>(500, "您没有该项目的配置权限,请联系管理员开通");
			Map<String, Object> emptyMap = new HashMap<String, Object>();
			emptyMap.put("data", new ArrayList<>());
			emptyMap.put("recordsTotal", 0);
			emptyMap.put("recordsFiltered", 0);
			return emptyMap;
		}

		// confNode in mysql
		List<ConfNode> data = confNodeDao.pageList(offset, pagesize, loginEnv, appname, key);
		int list_count = confNodeDao.pageListCount(offset, pagesize, loginEnv, appname, key);

		// package result
		Map<String, Object> maps = new HashMap<String, Object>();
		maps.put("data", data);
		maps.put("recordsTotal", list_count);		// 总记录数
		maps.put("recordsFiltered", list_count);	// 过滤后的总记录数
		return maps;

	}

	/**
	 *删除一个配置key，并保存到ConfNodeMsg
	 */
	@Override
	public ReturnT<String> delete(String key, ConfUser loginUser, String loginEnv) {
		if (StringUtils.isBlank(key)) {
			return new ReturnT<String>(500, "参数缺失");
		}
		ConfNode existNode = confNodeDao.load(loginEnv, key);
		if (existNode == null) {
			return new ReturnT<String>(500, "参数非法");
		}

		// project permission
		if (!ifHasProjectPermission(loginUser, loginEnv, existNode.getAppname())) {
			return new ReturnT<String>(500, "您没有该项目的配置权限,请联系管理员开通");
		}

		confNodeDao.delete(loginEnv, key);
		confNodeLogDao.deleteTimeout(loginEnv, key, 0);

		// conf msg
		sendConfMsg(loginEnv, key, null);

		return ReturnT.SUCCESS;
	}

	/**
	 *将被删除的配置key保存到confNodeMsg
	 */
	// conf broadcast msg
	private void sendConfMsg(String env, String key, String value){

		ConfNodeMsg confNodeMsg = new ConfNodeMsg();
		confNodeMsg.setEnv(env);
		confNodeMsg.setKey(key);
		confNodeMsg.setValue(value);

		confNodeMsgDao.add(confNodeMsg);
	}

	/**
	 *新增一个配置key
	 */
	@Override
	public ReturnT<String> add(ConfNode confNode, ConfUser loginUser, String loginEnv) {

		// valid
		if (StringUtils.isBlank(confNode.getAppname())) {
			return new ReturnT<String>(500, "AppName不可为空");
		}

		// project permission
		if (!ifHasProjectPermission(loginUser, loginEnv, confNode.getAppname())) {
			return new ReturnT<String>(500, "您没有该项目的配置权限,请联系管理员开通");
		}

		// valid group
		ConfProject group = confProjectDao.load(confNode.getAppname());
		if (group==null) {
			return new ReturnT<String>(500, "AppName非法");
		}

		// valid env
		if (StringUtils.isBlank(confNode.getEnv())) {
			return new ReturnT<String>(500, "配置Env不可为空");
		}
		ConfEnv confEnv = confEnvDao.load(confNode.getEnv());
		if (confEnv == null) {
			return new ReturnT<String>(500, "配置Env非法");
		}

		// valid key
		if (StringUtils.isBlank(confNode.getKey())) {
			return new ReturnT<String>(500, "配置Key不可为空");
		}
		confNode.setKey(confNode.getKey().trim());

		ConfNode existNode = confNodeDao.load(confNode.getEnv(), confNode.getKey());
		if (existNode != null) {
			return new ReturnT<String>(500, "配置Key已存在，不可重复添加");
		}
		if (!confNode.getKey().startsWith(confNode.getAppname())) {
			return new ReturnT<String>(500, "配置Key格式非法");
		}

		// valid title
		if (StringUtils.isBlank(confNode.getTitle())) {
			return new ReturnT<String>(500, "配置描述不可为空");
		}

		// value force null to ""
		if (confNode.getValue() == null) {
			confNode.setValue("");
		}

		// add node
		confNodeDao.insert(confNode);

		// node log
		ConfNodeLog nodeLog = new ConfNodeLog();
		nodeLog.setEnv(confNode.getEnv());
		nodeLog.setKey(confNode.getKey());
		nodeLog.setTitle(confNode.getTitle() + "(配置新增)" );
		nodeLog.setValue(confNode.getValue());
		nodeLog.setOptuser(loginUser.getUsername());
		confNodeLogDao.add(nodeLog);

		// conf msg
		sendConfMsg(confNode.getEnv(), confNode.getKey(), confNode.getValue());

		return ReturnT.SUCCESS;
	}

	/**
	 * 更新一个配置key
	 */
	@Override
	public ReturnT<String> update(ConfNode confNode, ConfUser loginUser, String loginEnv) {

		// valid
		if (StringUtils.isBlank(confNode.getKey())) {
			return new ReturnT<String>(500, "配置Key不可为空");
		}
		ConfNode existNode = confNodeDao.load(confNode.getEnv(), confNode.getKey());
		if (existNode == null) {
			return new ReturnT<String>(500, "配置Key非法");
		}

		// project permission
		if (!ifHasProjectPermission(loginUser, loginEnv, existNode.getAppname())) {
			return new ReturnT<String>(500, "您没有该项目的配置权限,请联系管理员开通");
		}

		if (StringUtils.isBlank(confNode.getTitle())) {
			return new ReturnT<String>(500, "配置描述不可为空");
		}

		// value force null to ""
		if (confNode.getValue() == null) {
			confNode.setValue("");
		}


		existNode.setTitle(confNode.getTitle());
		existNode.setValue(confNode.getValue());
		int ret = confNodeDao.update(existNode);
		if (ret < 1) {
			return ReturnT.FAIL;
		}

		// node log
		ConfNodeLog nodeLog = new ConfNodeLog();
		nodeLog.setEnv(existNode.getEnv());
		nodeLog.setKey(existNode.getKey());
		nodeLog.setTitle(existNode.getTitle() + "(配置更新)" );
		nodeLog.setValue(existNode.getValue());
		nodeLog.setOptuser(loginUser.getUsername());
		confNodeLogDao.add(nodeLog);
		confNodeLogDao.deleteTimeout(existNode.getEnv(), existNode.getKey(), 10);

		// conf msg
		sendConfMsg(confNode.getEnv(), confNode.getKey(), confNode.getValue());

		return ReturnT.SUCCESS;
	}
	// ---------------------- rest api ----------------------

	/**
	 * 查找一个key
	 */
	@Override
	public ReturnT<Map<String, String>> find(String accessToken, String env, List<String> keys) {

		// valid
		if (this.accessToken!=null && this.accessToken.trim().length()>0 && !this.accessToken.equals(accessToken)) {
			return new ReturnT<Map<String, String>>(ReturnT.FAIL.getCode(), "AccessToken Invalid.");
		}
		if (env==null || env.trim().length()==0) {
			return new ReturnT<>(ReturnT.FAIL.getCode(), "env Invalid.");
		}
		if (keys==null || keys.size()==0) {
			return new ReturnT<>(ReturnT.FAIL.getCode(), "keys Empty.");
		}

		// find key value
		Map<String, String> result = new HashMap<String, String>();
		for (String key: keys) {

			// valid key
			if (key==null || key.trim().length()<4 || key.trim().length()>100
					|| !RegexUtil.matches(RegexUtil.abc_number_line_point_pattern, key) ) {
				continue;
			}

			// get value
			String value = getFileConfData(env, key);
			if (value == null) {
			    continue;
            }

            // put
            result.put(key, value);
		}
		if (result.size() == 0) {
			return new ReturnT<>(ReturnT.FAIL.getCode(), "keys Invalid.");
		}

		return new ReturnT<Map<String, String>>(result);
	}

	/**
	 * 把环境下的配置key加入confDeferredResultMap，以实现监控
	 */
	@Override
	public DeferredResult<ReturnT<String>> monitor(String accessToken, String env, List<String> keys) {

		// init
		DeferredResult deferredResult = new DeferredResult(confBeatTime * 1000L, new ReturnT<>(ReturnT.FAIL.getCode(), "Monitor timeout."));

		// valid
		if (this.accessToken!=null && this.accessToken.trim().length()>0 && !this.accessToken.equals(accessToken)) {
			deferredResult.setResult(new ReturnT<>(ReturnT.FAIL.getCode(), "AccessToken Invalid."));
			return deferredResult;
		}
		if (env==null || env.trim().length()==0) {
			deferredResult.setResult(new ReturnT<>(ReturnT.FAIL.getCode(), "env Invalid."));
			return deferredResult;
		}
		if (keys==null || keys.size()==0) {
			deferredResult.setResult(new ReturnT<>(ReturnT.FAIL.getCode(), "keys Empty."));
			return deferredResult;
		}

		// monitor by client
		boolean monitorKey = false;
		for (String key: keys) {

		    // valid key
            if (key==null || key.trim().length()<4 || key.trim().length()>100
                    || !RegexUtil.matches(RegexUtil.abc_number_line_point_pattern, key) ) {
                continue;
            }
			monitorKey = true;

            // monitor key
			String fileName = parseConfDataFileName(env, key);

			List<DeferredResult> deferredResultList = confDeferredResultMap.get(fileName);
			if (deferredResultList == null) {
				deferredResultList = new ArrayList<>();
				confDeferredResultMap.put(fileName, deferredResultList);
			}

			deferredResultList.add(deferredResult);
		}

		if (!monitorKey) {
			deferredResult.setResult(new ReturnT<>(ReturnT.FAIL.getCode(), "keys Invalid."));
			return deferredResult;
		}

		return deferredResult;
	}


	// ---------------------- start stop ----------------------

	/**
	 * 启动线程
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		startThead();
	}

	/**
	 * 关闭线程
	 */
	@Override
	public void destroy() throws Exception {
		stopThread();
	}


	// ---------------------- thread ----------------------

	private ExecutorService executorService = Executors.newCachedThreadPool();
	private volatile boolean executorStoped = false;

	private volatile List<Integer> readedMessageIds = Collections.synchronizedList(new ArrayList<Integer>());

	private Map<String, List<DeferredResult>> confDeferredResultMap = new ConcurrentHashMap<>();

	/**
	 * 清理删除了的配置key，同步到file
	 */
	public void startThead() throws Exception {

		/**
		 * brocast conf-data msg, sync to file, for "add、update、delete"
		 */
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				while (!executorStoped) {
					try {
						// new message, filter readed
						List<ConfNodeMsg> messageList = confNodeMsgDao.findMsg(readedMessageIds);
						if (messageList!=null && messageList.size()>0) {
							for (ConfNodeMsg message: messageList) {
								readedMessageIds.add(message.getId());


								// sync file
								setFileConfData(message.getEnv(), message.getKey(), message.getValue());
							}
						}

						// clean old message;
						if ( (System.currentTimeMillis()/1000) % confBeatTime ==0) {
							confNodeMsgDao.cleanMessage(confBeatTime);
							readedMessageIds.clear();
						}
					} catch (Exception e) {
						if (!executorStoped) {
							logger.error(e.getMessage(), e);
						}
					}
					try {
						TimeUnit.SECONDS.sleep(1);
					} catch (Exception e) {
						if (!executorStoped) {
							logger.error(e.getMessage(), e);
						}
					}
				}
			}
		});


		/**
		 *  sync total conf-data, db + file      (1+N/30s)
		 *
		 *  clean deleted conf-data file
		 */
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				while (!executorStoped) {
					try {

						// sync registry-data, db + file
						int offset = 0;
						int pagesize = 1000;
						List<String> confDataFileList = new ArrayList<>();

						List<ConfNode> confNodeList = confNodeDao.pageList(offset, pagesize, null, null, null);
						while (confNodeList!=null && confNodeList.size()>0) {

							for (ConfNode confNoteItem: confNodeList) {

								// sync file
								String confDataFile = setFileConfData(confNoteItem.getEnv(), confNoteItem.getKey(), confNoteItem.getValue());

								// collect confDataFile
								confDataFileList.add(confDataFile);
							}


							offset += 1000;
							confNodeList = confNodeDao.pageList(offset, pagesize, null, null, null);
						}

						// clean old registry-data file
						cleanFileConfData(confDataFileList);

                        logger.info(">>>>>>>>>>> conf-admin, sync totel conf data success, sync conf count = {}", confDataFileList.size());
					} catch (Exception e) {
						if (!executorStoped) {
							logger.error(e.getMessage(), e);
						}
					}
					try {
						TimeUnit.SECONDS.sleep(confBeatTime);
					} catch (Exception e) {
						if (!executorStoped) {
							logger.error(e.getMessage(), e);
						}
					}
				}
			}
		});



	}

	/**
	 * 关闭线程池
	 */
	private void stopThread(){
		executorStoped = true;
		executorService.shutdownNow();
	}


	// ---------------------- file opt ----------------------

	// get
	public String getFileConfData(String env, String key){

		// fileName
		String confFileName = parseConfDataFileName(env, key);

		// read
		Properties existProp = PropUtil.loadFileProp(confFileName);
		if (existProp!=null && existProp.containsKey("value")) {
			return existProp.getProperty("value");
		}
		return null;
	}

	private String parseConfDataFileName(String env, String key){
		// fileName
		String fileName = confDataFilePath
				.concat(File.separator).concat(env)
				.concat(File.separator).concat(key)
				.concat(".properties");
		return fileName;
	}

	// set
	private String setFileConfData(String env, String key, String value){

		// fileName
		String confFileName = parseConfDataFileName(env, key);

		// valid repeat update
		Properties existProp = PropUtil.loadFileProp(confFileName);
		if (existProp != null
				&& value!=null
				&& value.equals(existProp.getProperty("value"))
				) {
			return new File(confFileName).getPath();
		}

		// write
		Properties prop = new Properties();
		if (value == null) {
			prop.setProperty("value-deleted", "true");
		} else {
			prop.setProperty("value", value);
		}

		PropUtil.writeFileProp(prop, confFileName);
		logger.info(">>>>>>>>>>> conf-admin, setFileConfData: confFileName={}, value={}", confFileName, value);

		// brocast monitor client
		List<DeferredResult> deferredResultList = confDeferredResultMap.get(confFileName);
		if (deferredResultList != null) {
			confDeferredResultMap.remove(confFileName);
			for (DeferredResult deferredResult: deferredResultList) {
				deferredResult.setResult(new ReturnT<>(501, "Monitor key update."));
			}
		}

		return new File(confFileName).getPath();
	}

	// clean
	public void cleanFileConfData(List<String> confDataFileList){
		filterChildPath(new File(confDataFilePath), confDataFileList);
	}

	public void filterChildPath(File parentPath, final List<String> confDataFileList){
		if (!parentPath.exists() || parentPath.list()==null || parentPath.list().length==0) {
			return;
		}
		File[] childFileList = parentPath.listFiles();
		for (File childFile: childFileList) {
			if (childFile.isFile() && !confDataFileList.contains(childFile.getPath())) {
				childFile.delete();

				logger.info(">>>>>>>>>>> conf-admin, cleanFileConfData, ConfDataFile={}", childFile.getPath());
			}
			if (childFile.isDirectory()) {
				if (parentPath.listFiles()!=null && parentPath.listFiles().length>0) {
					filterChildPath(childFile, confDataFileList);
				} else {
					childFile.delete();
				}

			}
		}

	}

}
