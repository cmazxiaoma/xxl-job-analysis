package com.xxl.job.quartz;

import com.xxl.job.admin.controller.AbstractSpringMvcTest;
import com.xxl.job.admin.core.schedule.XxlJobDynamicScheduler;
import com.xxl.job.admin.core.test.MyQuartzTask;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.quartz.*;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * @author xiaoma
 * @version V1.0
 * @Description: TODO
 * @date 2019/4/10 14:01
 */
public class QuartzTest extends AbstractSpringMvcTest {

    @Autowired
    private Scheduler scheduler;

    @Resource(name = "myQuartzJobBeanJobDetail")
    private JobDetail myQuartzJobBeanJobDetail;

    @Resource(name = "myQuartzTaskJobDetail")
    private JobDetail myQuartzTaskJobDetail;

    @Test
    public void testMethodInvokingJobBean() throws SchedulerException, InterruptedException {
        TriggerKey triggerKey = TriggerKey.triggerKey("simpleTrigger", "simpleTriggerGroup");

        SimpleScheduleBuilder simpleScheduleBuilder =
                SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(1)
                .withRepeatCount(1);

        SimpleTrigger simpleTrigger = (SimpleTrigger) TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startNow()
                .withSchedule(simpleScheduleBuilder)
                .build();

        scheduler.scheduleJob(myQuartzTaskJobDetail, simpleTrigger);

        TimeUnit.MINUTES.sleep(10);
    }

    @Test
    public void testJobDetailFactoryBean() throws InterruptedException, SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey("simpleTrigger1", "simpleTriggerGroup");

        SimpleScheduleBuilder simpleScheduleBuilder =
                SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(1)
                        .withRepeatCount(1);

        SimpleTrigger simpleTrigger = (SimpleTrigger) TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(simpleScheduleBuilder)
                .build();

        scheduler.scheduleJob(myQuartzJobBeanJobDetail, simpleTrigger);

        TimeUnit.MINUTES.sleep(10);
    }
}
