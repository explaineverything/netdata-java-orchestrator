// SPDX-License-Identifier: GPL-3.0-or-later

// Modified by piotr.walkiewicz@explaineverything.com at 06-16-2021

package org.firehol.netdata.module.jmx.utils;

import java.io.IOException;

import javax.management.*;

import org.firehol.netdata.module.jmx.exception.JmxMBeanServerQueryException;

public final class MBeanServerUtils {

	private MBeanServerUtils() {
	}

	public static Object getAttribute(MBeanServerConnection mBeanServer, ObjectName name, String attribute)
			throws JmxMBeanServerQueryException {

		if (mBeanServer == null) {
			throw new JmxMBeanServerQueryException(
					"Could not query attribute '" + attribute + "' of MBean '" + name + "'");
		}

		try {
			return mBeanServer.getAttribute(name, attribute);
		} catch (AttributeNotFoundException | InstanceNotFoundException | MBeanException | ReflectionException
				| IOException e) {
			throw new JmxMBeanServerQueryException(
					"Could not query attribute '" + attribute + "' of MBean '" + name + "'", e);
		}
	}
}
