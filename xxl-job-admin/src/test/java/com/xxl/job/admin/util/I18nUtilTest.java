package com.xxl.job.admin.util;

import com.xxl.job.admin.XxlJobAdminApplication;
import com.xxl.job.admin.core.util.I18nUtil;
import javafx.application.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * email util test
 *
 * @author xuxueli 2017-12-22 17:16:23
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = XxlJobAdminApplication.class)
public class I18nUtilTest {

    @Test
    public void test(){
        System.out.println(I18nUtil.getString("admin_name"));
        System.out.println(I18nUtil.getMultString("admin_name", "admin_name_full"));
    }

}
