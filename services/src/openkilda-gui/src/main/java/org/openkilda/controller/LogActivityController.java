package org.openkilda.controller;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

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

@RestController
@RequestMapping(path = "/useractivity", produces = MediaType.APPLICATION_JSON_VALUE)
public class LogActivityController extends BaseController {

    @Autowired
   private UserActivityLogService userActivityLogService;

    /**
     * UserManagement.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping
    public ModelAndView useractivity(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.ACTIVITY_LOGS);
    }

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(path = "/log", method = RequestMethod.GET)
	public List<LogInfo> getLogs(final @RequestParam(name = "userId", required = false) List<Long> userIds,
			final @RequestParam(name = "activity", required = false) List<String> activities,
			final @RequestParam(name = "startTime", required = false) String startTime,
			final @RequestParam(name = "endTime", required = false) String endTime) {
		return userActivityLogService.getActivityLog(userIds, activities, startTime, endTime);
	}

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(path = "/types", method = RequestMethod.GET)
    public List<ActivityTypeInfo> getActivityTypes() {
        List<ActivityTypeInfo> activityTypeInfos = new ArrayList<>();
        for (ActivityType activityType : ActivityType.values()) {
            activityTypeInfos.add(new ActivityTypeInfo(activityType.getId(),
                    activityType.getActivityTypeEntity().getActivityName()));
        }
        return activityTypeInfos;
    }
}
