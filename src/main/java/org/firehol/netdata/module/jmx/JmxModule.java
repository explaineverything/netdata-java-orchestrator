// SPDX-License-Identifier: GPL-3.0-or-later

// Modified by piotr.walkiewicz@explaineverything.com at 06-16-2021

package org.firehol.netdata.module.jmx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.firehol.netdata.exception.InitializationException;
import org.firehol.netdata.model.Chart;
import org.firehol.netdata.module.Module;
import org.firehol.netdata.module.jmx.configuration.JmxChartConfiguration;
import org.firehol.netdata.module.jmx.configuration.JmxModuleConfiguration;
import org.firehol.netdata.module.jmx.configuration.JmxServerConfiguration;
import org.firehol.netdata.module.jmx.exception.JmxMBeanServerConnectionException;
import org.firehol.netdata.module.jmx.exception.JmxMBeanServerQueryException;
import org.firehol.netdata.module.jmx.exception.VirtualMachineConnectionException;
import org.firehol.netdata.module.jmx.utils.VirtualMachineUtils;
import org.firehol.netdata.orchestrator.configuration.ConfigurationService;
import org.firehol.netdata.orchestrator.configuration.exception.ConfigurationSchemeInstantiationException;
import org.firehol.netdata.utils.LoggingUtils;
import org.firehol.netdata.utils.ResourceUtils;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * JmxModule collects metrics from JMX Servers.
 * 
 * <p>
 * The module also attatches to the local JMX server to monitor the orchestrator
 * process.
 * </p>
 * 
 * @see <a href=
 *      "http://www.oracle.com/technetwork/java/javase/tech/javamanagement-140525.html">Java
 *      Management Extensions (JMX) Technology</a>
 *
 */
public class JmxModule implements Module {

	private final Logger log = Logger.getLogger("org.firehol.netdata.module.jmx");

	private final ConfigurationService configurationService;

	private JmxModuleConfiguration configuration;

	private final List<MBeanServerCollector> allMBeanCollector = new ArrayList<>();

	public JmxModule(ConfigurationService configurationService) {
		this.configurationService = configurationService;
	}

	@Override
	public Collection<Chart> initialize() throws InitializationException {
		initConfiguration();
		connectToAllServer();
		return initCharts();
	}

	private void initConfiguration() throws InitializationException {
		readConfiguration();
		propagateCommonChartsToServerConfiguration();
	}

	private void readConfiguration() throws InitializationException {
		try {
			configuration = configurationService.readModuleConfiguration("jmx", JmxModuleConfiguration.class);
		} catch (ConfigurationSchemeInstantiationException e) {
			throw new InitializationException("Could not read jmx module configuration", e);
		}
	}

	private void propagateCommonChartsToServerConfiguration() {
		for (JmxServerConfiguration serverConfiguartion : configuration.getJmxServers()) {
			if (serverConfiguartion.getCharts() == null) {
				serverConfiguartion.setCharts(configuration.getCommonCharts());
				continue;
			}

			Map<String, JmxChartConfiguration> chartConfigById = chartConfigurationsById(
					serverConfiguartion.getCharts());

			for (JmxChartConfiguration chartConfig : configuration.getCommonCharts()) {
				chartConfigById.putIfAbsent(chartConfig.getId(), chartConfig);
			}

			List<JmxChartConfiguration> chartConfigs = new ArrayList<>(chartConfigById.values());
			serverConfiguartion.setCharts(chartConfigs);
		}
	}

	private Map<String, JmxChartConfiguration> chartConfigurationsById(List<JmxChartConfiguration> charts) {
		return charts.stream().collect(Collectors.toMap(JmxChartConfiguration::getId, Function.identity()));
	}

	private void connectToAllServer() {
		connectToConfiguredServers();

		// Disable connection to local process for now
		// connectToLocalProcess();

		if (configuration.isAutoDetectLocalVirtualMachines()) {
			connectToLocalServers();
		}
	}

	private void connectToConfiguredServers() {
		for (JmxServerConfiguration serverConfiguartion : configuration.getJmxServers()) {
			MBeanServerCollector collector;
			try {
				collector = buildMBeanServerCollector(serverConfiguartion);
			} catch (JmxMBeanServerConnectionException e) {
				log.warning(LoggingUtils.buildMessage(e));
				continue;
			}

			allMBeanCollector.add(collector);
		}
	}

	private MBeanServerCollector buildMBeanServerCollector(JmxServerConfiguration config)
			throws JmxMBeanServerConnectionException {

		return MBeanServerCollector.createCollector(config);
	}

	private void connectToLocalProcess() {
		JmxServerConfiguration localConfiguration = new JmxServerConfiguration();
		localConfiguration.setCharts(configuration.getCommonCharts());
		localConfiguration.setName("JavaPlugin");

		MBeanServerCollector collector = new MBeanServerCollector(localConfiguration,
				ManagementFactory.getPlatformMBeanServer());
		allMBeanCollector.add(collector);
	}

	private void connectToLocalServers() {
		Set<String> allRuntimeName = getAllMBeanCollectorRuntimeName();

		// List running VirtualMachines
		List<VirtualMachineDescriptor> virtualMachineDescriptors = VirtualMachine.list();
		for (VirtualMachineDescriptor virtualMachineDescriptor : virtualMachineDescriptors) {
			// Build the MBeanServerCollector
			MBeanServerCollector collector;
			try {
				collector = buildMBeanServerCollector(virtualMachineDescriptor);
			} catch (Exception e) {
				log.warning(LoggingUtils.getMessageSupplier(
						"Could not connect to JMX agent of process with PID " + virtualMachineDescriptor.id(), e));
				continue;
			}

			// Check if we already have a connection to this server...
			try {
				String runtimeName = collector.getRuntimeName();
				if (allRuntimeName.contains(runtimeName)) {
					// ... and close the connection if true.
					try {
						collector.close();
					} catch (IOException e) {
						log.warning(LoggingUtils.getMessageSupplier(
								"Could not close second connection to first configured and second auto detected JVM.",
								e));
					}
					continue;
				}
			} catch (JmxMBeanServerQueryException e) {
				log.warning(LoggingUtils.getMessageSupplier("Could not find runtimeName", e));
			}

			allMBeanCollector.add(collector);
		}
	}

	private Set<String> getAllMBeanCollectorRuntimeName() {
		Set<String> allRuntimeName = new HashSet<>();
		for (MBeanServerCollector mBeanCollector : allMBeanCollector) {
			try {
				String runtimeName = mBeanCollector.getRuntimeName();
				allRuntimeName.add(runtimeName);
			} catch (JmxMBeanServerQueryException e) {
				log.warning(LoggingUtils.getMessageSupplier("Could not find runtimeName", e));
			}
		}
		return allRuntimeName;
	}

	private MBeanServerCollector buildMBeanServerCollector(VirtualMachineDescriptor virtualMachineDescriptor)
			throws VirtualMachineConnectionException, JmxMBeanServerConnectionException {
		VirtualMachine virtualMachine = null;

		try {
			virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);

			JMXServiceURL serviceUrl;
			try {
				serviceUrl = VirtualMachineUtils.getJMXServiceURL(virtualMachine, true);
			} catch (IOException e) {
				throw new VirtualMachineConnectionException(
						"Could not get JMX ServiceUrl from Virtual Machine with PID " + virtualMachine.id(), e);

			}

			// Build configuration
			JmxServerConfiguration config = new JmxServerConfiguration();
			config.setServiceUrl(serviceUrl.toString());

			config.setName(virtualMachine.id());
			if (configuration != null) {
				config.setCharts(configuration.getCommonCharts());
			}

			// Build the MBeanServerCollector
			return buildMBeanServerCollector(config);

		} catch (AttachNotSupportedException | IOException e) {
			throw new VirtualMachineConnectionException(
					"Could not attatch to virtualMachine with PID " + virtualMachineDescriptor.id(), e);

		} finally {
			// Detatch from virtual machine.
			try {
				if (virtualMachine != null) {
					virtualMachine.detach();
				}
			} catch (IOException e) {
				log.warning(LoggingUtils.getMessageSupplier(
						"Could not detatch from virtual machine with PID " + virtualMachine.id(), e));
			}
		}
	}

	private Collection<Chart> initCharts() {
		List<Chart> allChart = new LinkedList<>();
		Iterator<MBeanServerCollector> mBeanCollectorIterator = allMBeanCollector.iterator();

		while (mBeanCollectorIterator.hasNext()) {
			MBeanServerCollector mBeanCollector = mBeanCollectorIterator.next();
			try {
				allChart.addAll(mBeanCollector.initialize());
			} catch (InitializationException e) {
				log.warning("Could not initialize JMX orchestrator " + mBeanCollector.getMBeanServer().toString());
				ResourceUtils.close(mBeanCollector);
				mBeanCollectorIterator.remove();
			}
		}

		return allChart;
	}

	public void cleanup() {
		try {
			CompletableFuture
					.allOf(allMBeanCollector.stream().map(ResourceUtils::close).toArray(CompletableFuture[]::new))
					.get();
		} catch (InterruptedException | ExecutionException e) {
			log.fine("Could not close connection to at least one JMX Server");
		}

	}

	@Override
	public Collection<Chart> collectValues() {
		return allMBeanCollector.stream().map(MBeanServerCollector::collectValues).flatMap(Collection::stream).collect(
				Collectors.toList());
	}

	@Override
	public String getName() {
		return "jmx";
	}

}
