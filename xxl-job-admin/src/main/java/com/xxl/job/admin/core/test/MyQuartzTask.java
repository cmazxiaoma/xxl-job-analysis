package com.xxl.job.admin.core.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author xiaoma
 * @version V1.0
 * @Description: TODO
 * @date 2019/4/10 13:50
 */
@Component(value = "MyQuartzTask")
public class MyQuartzTask {

    @Autowired
    private MyQuartzService myQuartzService;

    public void execute() {
        System.out.println("MyQuartzTask");
        myQuartzService.print();
    }
}
