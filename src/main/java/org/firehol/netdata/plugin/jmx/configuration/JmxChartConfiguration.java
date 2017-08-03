/*
 * Copyright (C) 2017 Simon Nagl
 *
 * netadata-plugin-java-daemon is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.firehol.netdata.plugin.jmx.configuration;

import java.util.ArrayList;
import java.util.List;

import org.firehol.netdata.entity.ChartType;
import org.firehol.netdata.entity.DimensionAlgorithm;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JmxChartConfiguration {
	private String id;
	private String title;
	private String units;
	private Integer priority;
	private ChartType chartType = ChartType.LINE;

	private DimensionAlgorithm dimType = DimensionAlgorithm.ABSOLUTE;

	private List<JmxDimensionConfiguration> dimensions = new ArrayList<>();
}
