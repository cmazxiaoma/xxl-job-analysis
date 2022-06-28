package com.xxl.job.admin.core.test;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author xiaoma
 * @version V1.0
 * @Description: TODO
 * @date 2019/4/10 17:16
 */
public class MyMethodInvokingJobBean extends QuartzJobBean implements ApplicationContextAware {

    private String targetObject;
    private String targetMethod;
    private ApplicationContext applicationContext;

    public void setTargetObject(String targetObject) {
        this.targetObject = targetObject;
    }

    public void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Object object = applicationContext.getBean(this.targetObject);
        System.out.println("targetObject:" + targetObject);
        System.out.println("targetMethod:" + targetMethod);
        try {
            Method method = object.getClass().getMethod(this.targetMethod, new Class[] {});
            method.invoke(object, new Object[]{});
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
