package com.xxl.job;

import com.xxl.job.core.util.DateUtil;

import java.util.Date;

/**
 * @author xiaoma
 * @version V1.0
 * @Description: TODO
 * @date 2020/3/13 9:07
 */
public class Test {

    public static void main(String[] args) {
        StackTraceElement callInfo = new Throwable().getStackTrace()[0];
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(DateUtil.format(new Date())).append(" ")
                .append("["+ callInfo.getClassName() + "#" + callInfo.getMethodName() +"]").append("-")
                .append("["+ callInfo.getLineNumber() +"]").append("-")
                .append("["+ Thread.currentThread().getName() +"]").append(" ");
        String formatAppendLog = stringBuffer.toString();
        System.out.println(formatAppendLog);
    }


}
