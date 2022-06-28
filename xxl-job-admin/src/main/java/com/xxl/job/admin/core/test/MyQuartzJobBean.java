package com.xxl.job.admin.core.test;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * @author xiaoma
 * @version V1.0
 * @Description: TODO
 * @date 2019/4/10 11:05
 */
public class MyQuartzJobBean extends QuartzJobBean {

    @Autowired
    private MyQuartzService myQuartzService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        System.out.println("MyQuartzJobBean executeInternal");
        myQuartzService.print();
    }

}
