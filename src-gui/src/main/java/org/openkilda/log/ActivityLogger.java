/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.log;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.log.model.LogInfo;
import org.openkilda.log.service.UserActivityService;

import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class ActivityLogger {
    private static final Logger LOGGER = Logger.getLogger(ActivityLogger.class);

    private static final BlockingQueue<LogInfo> logs = new LinkedBlockingQueue<>();
    private static Boolean isThreadStarted = false;

    @Autowired
    private ServerContext serverContext;

    @Autowired
    private UserActivityService userActivityService;

    public ActivityLogger() {
        synchronized (logs) {
            if (!isThreadStarted) {
                Thread thread = new Thread(new ActivityLogProcessor());
                thread.start();
                isThreadStarted = true;
            }
        }
    }

    public void log(final ActivityType activityType) {
        LogInfo logInfo = getLogInfo(activityType, null);
        log(logInfo);
    }

    public void log(final ActivityType activityType, final String objectId) {
        LogInfo logInfo = getLogInfo(activityType, objectId);
        log(logInfo);
    }

    /**
     * Log.
     *
     * @param logInfo the log info
     */
    public void log(final LogInfo logInfo) {
        if (logInfo != null) {
            try {
                logs.put(logInfo);
            } catch (InterruptedException e) {
                LOGGER.error("Error occurred while adding logs for logging user activity", e);
            }
        }
    }

    private LogInfo getLogInfo(final ActivityType activityType, final String objectId) {
        LogInfo logInfo = new LogInfo();
        RequestContext requestContext = serverContext.getRequestContext();

        logInfo.setUserId(requestContext.getUserId());
        logInfo.setActivityType(activityType);
        logInfo.setObjectId(objectId);
        logInfo.setActivityTime(Calendar.getInstance().getTime());
        logInfo.setClientIpAddress(requestContext.getClientIpAddress());

        return logInfo;
    }

    public class ActivityLogProcessor implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    LogInfo logInfo = logs.take();
                    userActivityService.logUserActivity(logInfo);
                } catch (Exception e) {
                    LOGGER.error("Error occurred while logging user activity", e);
                }
            }
        }
    }
}
