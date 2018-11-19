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

package org.openkilda.controller;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.log.model.ActivityTypeInfo;
import org.openkilda.log.model.LogInfo;
import org.openkilda.service.UserActivityLogService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * The Class LogActivityController.
 */

@RestController
@RequestMapping(path = "/api/useractivity", produces = MediaType.APPLICATION_JSON_VALUE)
public class LogActivityController extends BaseController {

    /** The user activity log service. */
    @Autowired
    private UserActivityLogService userActivityLogService;

    /**
     * UserManagement.
     *
     * @param request the request
     * @return the model and view
     */
    @RequestMapping
    @Permissions(values = { IConstants.Permission.MENU_USER_ACTIVITY })
    public ModelAndView useractivity(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.ACTIVITY_LOGS);
    }

    /**
     * Gets the logs.
     *
     * @param userIds the user ids
     * @param activities the activities
     * @param startTime the start time
     * @param endTime the end time
     * @return the logs
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(path = "/log", method = RequestMethod.GET)
    public List<LogInfo> getLogs(final @RequestParam(name = "userId", required = false) List<Long> userIds,
            final @RequestParam(name = "activity", required = false) List<String> activities,
            final @RequestParam(name = "startTime", required = false) String startTime,
            final @RequestParam(name = "endTime", required = false) String endTime) {
        return userActivityLogService.getActivityLog(userIds, activities, startTime, endTime);
    }

    /**
     * Gets the activity types.
     *
     * @return the activity types
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(path = "/types", method = RequestMethod.GET)
    public List<ActivityTypeInfo> getActivityTypes() {
        List<ActivityTypeInfo> activityTypeInfos = new ArrayList<>();
        for (ActivityType activityType : ActivityType.values()) {
            activityTypeInfos.add(
                    new ActivityTypeInfo(activityType.getId(), activityType.getActivityTypeEntity().getActivityName()));
        }
        return activityTypeInfos;
    }
}
