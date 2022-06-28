package com.xxl.job.admin.core.test;

import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * @author xiaoma
 * @version V1.0
 * @Description: TODO
 * @date 2019/4/10 15:25
 */
@Configuration
public class MyQuartzConfiguration {

    @Autowired
    private MyQuartzTask myQuartzTask;

    @Bean(name = "myQuartzJobBeanJobDetail")
    public JobDetailFactoryBean myQuartzJobBeanJobDetail() {
        JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
        jobDetailFactoryBean.setJobClass(MyQuartzJobBean.class);
        jobDetailFactoryBean.setDurability(true);
        jobDetailFactoryBean.setRequestsRecovery(true);
        return jobDetailFactoryBean;
    }

    @Bean(name = "myQuartzTaskJobDetail")
    public JobDetailFactoryBean myQuartzTaskJobDetail() {
        JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
        jobDetailFactoryBean.setRequestsRecovery(true);
        jobDetailFactoryBean.setDurability(true);
        jobDetailFactoryBean.getJobDataMap().put("targetObject", "MyQuartzTask");
        jobDetailFactoryBean.getJobDataMap().put("targetMethod", "execute");
        jobDetailFactoryBean.setJobClass(MyMethodInvokingJobBean.class);
        return jobDetailFactoryBean;
    }
}
