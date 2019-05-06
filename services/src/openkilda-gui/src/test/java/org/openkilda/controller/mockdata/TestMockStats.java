package org.openkilda.controller.mockdata;

import java.text.SimpleDateFormat;
import java.util.Date;



public class TestMockStats{
	public static final String FLOW_ID = "f75a6d6ee59744a8";
	@SuppressWarnings("deprecation")
	public static final String START_DATE = newDate(new Date("2019/03/01"));
	public static final String END_DATE = newDate(new Date(new Date().getTime() - 24 * 60 * 60 * 1000));
	public static final String METRIC_BITS = "bits";
	public static final String METRIC_BYTES = "bytes";
	public static final String METRIC_PACKETS = "packets";
	public static final String DIRECTION_FORWARD = "forward";
	public static final String DIRECTION_REVERSE = "forward";
	public static final String DIRECTION_BOTH = "null";
	public static final String DOWNSAMPLE ="10m";

	static String newDate(Date date) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-hh:mm:ss");
		String strDate = formatter.format(date);
		return strDate;
	}

}