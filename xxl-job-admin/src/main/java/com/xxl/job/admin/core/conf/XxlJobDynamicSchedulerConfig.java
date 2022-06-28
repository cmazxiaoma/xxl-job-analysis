package com.xxl.job.admin.core.conf;

import com.xxl.job.admin.core.schedule.XxlJobDynamicScheduler;
import com.xxl.job.admin.core.test.AutowireSpringBeanJobFactory;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;

/**
 * @author xuxueli 2018-10-28 00:18:17
 */
@Configuration
public class XxlJobDynamicSchedulerConfig {

    @Autowired
    private AutowireSpringBeanJobFactory autowireSpringBeanJobFactory;


    @Bean
    public SchedulerFactoryBean getSchedulerFactoryBean(DataSource dataSource){

        SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        schedulerFactory.setDataSource(dataSource);
        // 自动启动
        schedulerFactory.setAutoStartup(true);
        // 延时启动，应用启动成功后在启动
        schedulerFactory.setStartupDelay(20);
        // 覆盖DB中JOB：true、以数据库中已经存在的为准：false
        schedulerFactory.setOverwriteExistingJobs(true);
        schedulerFactory.setApplicationContextSchedulerContextKey("applicationContext");
        schedulerFactory.setConfigLocation(new ClassPathResource("quartz.properties"));
        schedulerFactory.setJobFactory(autowireSpringBeanJobFactory);

        return schedulerFactory;
    }

    @Bean(initMethod = "start", destroyMethod = "destroy")
    public XxlJobDynamicScheduler getXxlJobDynamicScheduler(SchedulerFactoryBean schedulerFactory){

        Scheduler scheduler = schedulerFactory.getScheduler();

        XxlJobDynamicScheduler xxlJobDynamicScheduler = new XxlJobDynamicScheduler();
        xxlJobDynamicScheduler.setScheduler(scheduler);

        return xxlJobDynamicScheduler;
    }

}
