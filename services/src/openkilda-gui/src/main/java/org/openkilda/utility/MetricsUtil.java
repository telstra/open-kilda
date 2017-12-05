package org.openkilda.utility;

import java.util.ArrayList;
import java.util.List;

/**
 * The Class MetricsUtil.
 */
public final class MetricsUtil {

	/** The Constant PEN_FLOW_BITS. */
	public static final String PEN_FLOW_BITS = "pen.flow.bits";

	/** The Constant PEN_FLOW_BYTES. */
	public static final String PEN_FLOW_BYTES = "pen.flow.bytes";

	/** The Constant PEN_FLOW_PACKETS. */
	public static final String PEN_FLOW_PACKETS = "pen.flow.packets";

	/** The Constant PEN_FLOW_TABLEID. */
	public static final String PEN_FLOW_TABLEID = "pen.flow.tableid";

	/** The Constant PEN_ISL_LATENCY. */
	public static final String PEN_ISL_LATENCY = "pen.isl.latency";

	/** The Constant PEN_SWITCH_COLLISIONS. */
	public static final String PEN_SWITCH_COLLISIONS = "pen.switch.collisions";

	/** The Constant PEN_SWITCH_RX_BITS. */
	public static final String PEN_SWITCH_RX_BITS = "pen.switch.rx-bits";

	/** The Constant PEN_SWITCH_RX_BYTES. */
	public static final String PEN_SWITCH_RX_BYTES = "pen.switch.rx-bytes";

	/** The Constant PEN_SWITCH_RX_CRC_ERROR. */
	public static final String PEN_SWITCH_RX_CRC_ERROR = "pen.switch.rx-crc-error";

	/** The Constant PEN_SWITCH_RX_DROPPED. */
	public static final String PEN_SWITCH_RX_DROPPED = "pen.switch.rx-dropped";

	/** The Constant PEN_SWITCH_RX_ERRORS. */
	public static final String PEN_SWITCH_RX_ERRORS = "pen.switch.rx-errors";

	/** The Constant PEN_SWITCH_RX_FRAME_ERROR. */
	public static final String PEN_SWITCH_RX_FRAME_ERROR = "pen.switch.rx-frame-error";

	/** The Constant PEN_SWITCH_RX_OVER_ERROR. */
	public static final String PEN_SWITCH_RX_OVER_ERROR = "pen.switch.rx-over-error";

	/** The Constant PEN_SWITCH_RX_PACKETS. */
	public static final String PEN_SWITCH_RX_PACKETS = "pen.switch.rx-packets";

	/** The Constant PEN_SWITCH_TX_BITS. */
	public static final String PEN_SWITCH_TX_BITS = "pen.switch.tx-bits";

	/** The Constant PEN_SWITCH_TX_BYTES. */
	public static final String PEN_SWITCH_TX_BYTES = "pen.switch.tx-bytes";

	/** The Constant PEN_SWITCH_TX_DROPPED. */
	public static final String PEN_SWITCH_TX_DROPPED = "pen.switch.tx-dropped";

	/** The Constant PEN_SWITCH_TX_ERRORS. */
	public static final String PEN_SWITCH_TX_ERRORS = "pen.switch.tx-errors";

	/** The Constant PEN_SWITCH_TX_PACKETS. */
	public static final String PEN_SWITCH_TX_PACKETS = "pen.switch.tx-packets";

	/**
	 * Gets the metric list.
	 *
	 * @return the metric list
	 */
	public static List<String> getMetricList() {

		List<String> list = new ArrayList<String>();

		list.add(PEN_FLOW_BITS);
		list.add(PEN_FLOW_BYTES);
		list.add(PEN_FLOW_PACKETS);
		list.add(PEN_FLOW_TABLEID);
		list.add(PEN_ISL_LATENCY);

		list.add(PEN_SWITCH_COLLISIONS);
		list.add(PEN_SWITCH_RX_BITS);
		list.add(PEN_SWITCH_RX_BYTES);
		list.add(PEN_SWITCH_RX_CRC_ERROR);
		list.add(PEN_SWITCH_RX_DROPPED);
		list.add(PEN_SWITCH_RX_ERRORS);

		list.add(PEN_SWITCH_RX_FRAME_ERROR);
		list.add(PEN_SWITCH_RX_OVER_ERROR);
		list.add(PEN_SWITCH_RX_PACKETS);
		list.add(PEN_SWITCH_TX_BITS);

		list.add(PEN_SWITCH_TX_BYTES);
		list.add(PEN_SWITCH_TX_DROPPED);
		list.add(PEN_SWITCH_TX_ERRORS);
		list.add(PEN_SWITCH_TX_PACKETS);

		return list;
	}
}
