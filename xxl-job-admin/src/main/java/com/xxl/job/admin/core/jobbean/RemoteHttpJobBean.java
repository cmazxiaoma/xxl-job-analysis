package com.xxl.job.admin.core.jobbean;

import com.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * http job bean
 * “@DisallowConcurrentExecution” diable concurrent, thread size can not be only one, better given more
 * @author xuxueli 2015-12-17 18:20:34
 */

/**
 * 常规Quartz的开发，任务逻辑一般都维护在QuartzJobBean中，耦合很严重。
 * xxl-job中调度模块和任务模块完全耦合，调度模块中的所有调度任务使用
 * 同一个QuartzJobBean,即RemoteHttpJobBean。
 *
 * 不同的调度任务将各自参数维护在各自扩展表数据中，
 * 当触发RemoteHttpJobBean执行时，将会解析不同的任务参数发起远程调用，
 * 调用各自的远程执行器服务。
 */

/**
 * @DisallowConcurrentExecution
 * xxl-job调度模块的调度中心默认不使用该注解，即默认开启并行机制，
 * RemoteHttpJobBean为公共QuartzJobBean,这样在多线程调度的情况下，
 * 调度模块被阻塞的几率很低，大大提高了调度系统的承载量。
 *
 * xxl-job的每个调度任务虽然在调度模块是并行调度执行的，但是任务调度
 * 传递到任务模块的执行器是串行执行的，同时支持任务终止。
 *
 *
 */
public class RemoteHttpJobBean extends QuartzJobBean {
	private static Logger logger = LoggerFactory.getLogger(RemoteHttpJobBean.class);

	/**
	 * xxl-job所有的任务触发最终都是通过这个类来执行
	 * RemoteHttpJobBean => QuartzJobBean => JobThread
	 *
	 * @param context
	 * @throws JobExecutionException
	 */
	@Override
	protected void executeInternal(JobExecutionContext context)
			throws JobExecutionException {

		// load jobId
		JobKey jobKey = context.getTrigger().getJobKey();
		Integer jobId = Integer.valueOf(jobKey.getName());

		// trigger
		/**
		 * 调度任务
		 */
		JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.CRON, -1, null, null);
	}

}