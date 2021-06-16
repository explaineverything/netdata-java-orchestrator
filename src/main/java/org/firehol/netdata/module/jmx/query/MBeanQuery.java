// SPDX-License-Identifier: GPL-3.0-or-later

// Modified by piotr.walkiewicz@explaineverything.com at 06-16-2021

package org.firehol.netdata.module.jmx.query;

import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.xml.ws.Holder;

import org.firehol.netdata.model.Dimension;
import org.firehol.netdata.module.jmx.exception.JmxMBeanServerQueryException;
import org.firehol.netdata.module.jmx.utils.MBeanServerUtils;

import lombok.Getter;

/**
 * MBeanQuery is able to query one attribute of a MBeanServer and update the
 * currentValue of added Dimensions.
 *
 * <p>
 * Supported attributes are
 *
 * <ul>
 * <li>Simple attributes which return long, int or double</li>
 * <li>Composite attributes which may return more than one value at once. For
 * this cases you have to add the attribute in format
 * {@code <attribute_to_query>.<key_of_the_result_to_store>}</li>
 * </ul>
 * </p>
 */
@Getter
public abstract class MBeanQuery {
	private final ObjectName name;

	private final String attribute;

	private final Holder<MBeanServerConnection> mBeanServer;

	private boolean enabled = false;

	MBeanQuery(Holder<MBeanServerConnection> mBeanServer, final ObjectName name, final String attribute) {
		this.mBeanServer = mBeanServer;
		this.name = name;
		this.attribute = attribute;
	}

	public static MBeanQuery newInstance(Holder<MBeanServerConnection> mBeanServer, final ObjectName mBeanName,
			final String attribute) throws JmxMBeanServerQueryException {
		final String mBeanAttribute = attribute.split("\\.", 2)[0];
		final Object queryResult = MBeanServerUtils.getAttribute(mBeanServer.value, mBeanName, mBeanAttribute);

		if (CompositeData.class.isAssignableFrom(queryResult.getClass())) {

			if (attribute.contains("/")) {
				return new MBeanCompositePercentDataQuery(mBeanServer, mBeanName, mBeanAttribute);
			} else {
				return new MBeanCompositeDataQuery(mBeanServer, mBeanName, mBeanAttribute);
			}
		}

		return new MBeanSimpleQuery(mBeanServer, mBeanName, mBeanAttribute, MBeanValueStore.newInstance(queryResult));
	}

	public abstract void addDimension(Dimension dimension, String attribute) throws JmxMBeanServerQueryException;

	public abstract void query() throws JmxMBeanServerQueryException;

	public abstract List<Dimension> getDimensions();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
