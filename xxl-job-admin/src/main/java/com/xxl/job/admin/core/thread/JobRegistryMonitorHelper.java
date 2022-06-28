package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobRegistry;
import com.xxl.job.admin.core.schedule.XxlJobDynamicScheduler;
import com.xxl.job.core.enums.RegistryConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * job registry instance
 * @author xuxueli 2016-10-02 19:10:24
 */
public class JobRegistryMonitorHelper {
	private static Logger logger = LoggerFactory.getLogger(JobRegistryMonitorHelper.class);

	private static JobRegistryMonitorHelper instance = new JobRegistryMonitorHelper();
	public static JobRegistryMonitorHelper getInstance(){
		return instance;
	}

	private Thread registryThread;
	private volatile boolean toStop = false;

	/**
	 * AppName: 每个执行器机器集群的唯一标示, 任务注册以 "执行器" 为最小粒度进行注册;
	 *
	 * 每个任务通过其绑定的执行器可感知对应的执行器机器列表;
	 注册表: 见"XXL_JOB_QRTZ_TRIGGER_REGISTRY"表,

	 * "执行器" 在进行任务注册时将会周期性维护一条注册记录，即机器地址和AppName的绑定关系;
	 "调度中心" 从而可以动态感知每个AppName在线的机器列表;
	 执行器注册: 任务注册Beat周期默认30s; 执行器以一倍Beat进行执行器注册,
	 调度中心以一倍Beat进行动态任务发现; 注册信息的失效时间是三倍Beat;

	 执行器注册摘除：执行器销毁时，将会主动上报调度中心并摘除对应的执行器机器信息，提高心跳注册的实时性；
	 */
	public void start(){
		registryThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!toStop) {
					try {
						// auto registry group
						/**
						 * 获取类型为自动注册的执行器地址列表
						 */
						List<XxlJobGroup> groupList = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().findByAddressType(0);
						if (CollectionUtils.isNotEmpty(groupList)) {

							/**
							 * 删除90s之内没有更新信息的注册机器，90s没有心跳信息返回，
							 * 代表机器已经出现问题，所以移除。
							 *
							 */
							// remove dead address (admin/executor)
							XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().removeDead(RegistryConfig.DEAD_TIMEOUT);

							// fresh online address (admin/executor)
							// 查询在90s之内有过更新的机器列表
							HashMap<String, List<String>> appAddressMap = new HashMap<String, List<String>>();
							List<XxlJobRegistry> list = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().findAll(RegistryConfig.DEAD_TIMEOUT);
							if (list != null) {
								for (XxlJobRegistry item: list) {
									if (RegistryConfig.RegistType.EXECUTOR.name().equals(item.getRegistryGroup())) {
										String appName = item.getRegistryKey();
										List<String> registryList = appAddressMap.get(appName);
										if (registryList == null) {
											registryList = new ArrayList<String>();
										}

										if (!registryList.contains(item.getRegistryValue())) {
											registryList.add(item.getRegistryValue());
										}
										// 收集机器信息，根据机器名区分
										appAddressMap.put(appName, registryList);
									}
								}
							}

							// fresh group address
							for (XxlJobGroup group: groupList) {
								List<String> registryList = appAddressMap.get(group.getAppName());
								String addressListStr = null;
								if (CollectionUtils.isNotEmpty(registryList)) {
									Collections.sort(registryList);
									addressListStr = StringUtils.join(registryList, ",");
								}
								// 得出这个执行器的集群ip列表
								group.setAddressList(addressListStr);
								XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().update(group);
							}
						}
					} catch (Exception e) {
						logger.error("job registry instance error:{}", e);
					}
					try {
						TimeUnit.SECONDS.sleep(RegistryConfig.BEAT_TIMEOUT);
					} catch (InterruptedException e) {
						logger.error("job registry instance error:{}", e);
					}
				}
			}
		});
		registryThread.setDaemon(true);
		registryThread.start();
	}

	public void toStop(){
		toStop = true;
		// interrupt and wait
		registryThread.interrupt();
		try {
			registryThread.join();
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}

}
