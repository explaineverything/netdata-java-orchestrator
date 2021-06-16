// SPDX-License-Identifier: GPL-3.0-or-later

// Created by piotr.walkiewicz@explaineverything.com at 06-16-2021

package org.firehol.netdata.module.jmx.query;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.xml.ws.Holder;

import org.firehol.netdata.model.Dimension;
import org.firehol.netdata.module.jmx.exception.JmxMBeanServerQueryException;
import org.firehol.netdata.module.jmx.utils.MBeanServerUtils;

public class MBeanCompositePercentDataQuery extends MBeanQuery {

	MBeanValueStore percentageValue;

	private boolean complementaryValue = false;
	private String numeratorKey;
	private String denumeratorKey;

	MBeanCompositePercentDataQuery(Holder<MBeanServerConnection> mBeanServer, final ObjectName name,
			final String attribute) {
		super(mBeanServer, name, attribute);
	}

	@Override
	public void addDimension(Dimension dimension, final String attribute) throws JmxMBeanServerQueryException {

		complementaryValue = attribute.endsWith("-");
		String attributeTmp = attribute.substring(0, attribute.length() - 1);

		final String[] splitString = attributeTmp.split("\\/");

		if (splitString.length != 2) {
			throw new IllegalArgumentException(String.format(
					"Expected attribute to be in format '<attribute>.<key>/<attribute>.<key>', but was '%s'",
					attribute));
		}

		String numeratorPart = splitString[0];
		String denumeratorPart = splitString[1];

		final String[] numeratorSplit = numeratorPart.split("\\.");
		final String[] denumeratorSplit = denumeratorPart.split("\\.");

		if (numeratorSplit.length != 2 || denumeratorSplit.length != 2) {
			throw new IllegalArgumentException(String.format(
					"Expected attribute to be in format '<attribute>.<key>/<attribute>.<key>', but was '%s'",
					attribute));
		}

		numeratorKey = numeratorSplit[1];
		denumeratorKey = denumeratorSplit[1];

		final CompositeData queryResult = queryServer();
		final Object numberatorValue = queryResult.get(numeratorKey);
		final Object denumeratorValue = queryResult.get(denumeratorKey);

		if (!isNumeric(numberatorValue) || !isNumeric(denumeratorValue)) {
			throw new IllegalArgumentException(
					String.format("Expected numerator and denumerator to have numberic type, but was '%s' and '%s'",
							numberatorValue.getClass().toString(), denumeratorValue.getClass().toString()));
		}

		percentageValue = new MBeanDoubleStore();
		percentageValue.addDimension(dimension);
	}

	private boolean isNumeric(Object value) {

		if (value instanceof Double) {
			return true;
		} else if (value instanceof Integer) {
			return true;
		} else if (value instanceof Long) {
			return true;
		}

		return false;
	}

	private Double getDouble(Object value) {

		if (value instanceof Double) {
			return (Double) value;
		} else if (value instanceof Integer) {
			return new Double((Integer) value);
		} else if (value instanceof Long) {
			return new Double((Long) value);
		} else {
			return null;
		}
	}

	private Double computePercentage(final CompositeData compositeData) {

		final Object numberatorValue = compositeData.get(numeratorKey);
		final Object denumeratorValue = compositeData.get(denumeratorKey);

		Double numeratorDouble = getDouble(numberatorValue);
		Double denumeratorDouble = getDouble(denumeratorValue);

		if (numeratorDouble == null || denumeratorDouble == null) {
			return null;
		}

		if (!complementaryValue) {
			return numeratorDouble.doubleValue() / denumeratorDouble.doubleValue();
		} else {
			return 1 - (numeratorDouble.doubleValue() / denumeratorDouble.doubleValue());
		}
	}

	@Override
	public List<Dimension> getDimensions() {
		return percentageValue.getAllDimension();
	}

	@Override
	public void query() throws JmxMBeanServerQueryException {
		final CompositeData compositeData = queryServer();

		Double percentage = computePercentage(compositeData);
		if (percentage != null) {
			percentageValue.updateValue(percentage.doubleValue() * 1000);
		}
	}

	private CompositeData queryServer() throws JmxMBeanServerQueryException {
		return (CompositeData) MBeanServerUtils.getAttribute(getMBeanServer().value, this.getName(),
				this.getAttribute());
	}
}
