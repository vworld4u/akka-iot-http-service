package com.akkasamples.iottempmanager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.akkasamples.iottempmanager.DeviceGroup.DeviceTerminated;
import com.akkasamples.iottempmanager.DeviceGroup.DeviceTimedout;
import com.akkasamples.iottempmanager.DeviceGroup.TemperatureAvailable;
import com.akkasamples.iottempmanager.DeviceGroup.TemperatureNotAvailable;
import com.akkasamples.iottempmanager.DeviceGroup.TemperatureReading;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;

public class DeviceManager extends AbstractActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	private Map<String, ActorRef> groupToActors = new HashMap<>();
	private Map<ActorRef, String> actorsToGroups = new HashMap<>();

	public static Props props() {
		return Props.create(DeviceManager.class);
	}

	@Override
	public void preStart() throws Exception {
		log.info("DeviceManager.preStart()");
		super.preStart();
	}

	@Override
	public void postStop() throws Exception {
		log.info("DeviceManager.postStop()");
		super.postStop();
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(RequestRegisterDevice.class, this::onRegisterDevice)
				.match(DeviceManager.RecordTemperature.class, r -> {
					if (groupToActors.containsKey(r.groupId)) {
						log.info(String.format("DeviceManager.recordDeviceTemperature(%s, %s, %f):", r.groupId, r.deviceId, r.temperature));
						ActorRef deviceGroupActor = groupToActors.get(r.groupId);
						deviceGroupActor.tell(r, getSelf());
					} else {
						log.warning(String.format("DeviceManager.recordDeviceTemperature(%s, %s, %f): Device Group not Found", r.groupId, r.deviceId, r.temperature));
					}
				})
				.match(DeviceManager.ReadDeviceTemperature.class, r -> {
					if (groupToActors.containsKey(r.groupId)) {
						ActorRef deviceGroupActor = groupToActors.get(r.groupId);
						Timeout timeout = new Timeout(100, TimeUnit.MILLISECONDS);
						Future<Object> result = Patterns.ask(deviceGroupActor, r, timeout);
						try {
							DeviceTemperature reply = (DeviceTemperature)Await.result(result, timeout.duration());
							getSender().tell(new DeviceTemperature(r.requestId, r.groupId, r.deviceId, reply.temperature), getSelf());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				})
				.match(RequestDeviceList.class, r -> {
					log.info(String.format("DeviceManager.requestDeviceList(%s): IsPresent: %b", r.groupId, groupToActors.containsKey(r.groupId)));
					if (groupToActors.containsKey(r.groupId)) {
						ActorRef deviceGroupActor = groupToActors.get(r.groupId);
						Timeout timeout = new Timeout(1000, TimeUnit.MILLISECONDS);
						log.info(String.format("DeviceManager.requestDeviceList(%s): Asking for Device List", r.groupId));
						Future<Object> result = Patterns.ask(deviceGroupActor, new DeviceGroup.RequestDeviceList(r.requestId), timeout);
						try {
							DeviceGroup.RespondDeviceList reply = (DeviceGroup.RespondDeviceList)Await.result(result, timeout.duration());
							log.info(String.format("DeviceManager.requestDeviceList(%s): DeviceList: %s", r.groupId, reply.devices));
							getSender().tell(new DeviceList(r.requestId, r.groupId, reply.devices), getSelf());
							log.info(String.format("DeviceManager.requestDeviceList(%s): DeviceList: %s", r.groupId, reply.devices));
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else {
						log.warning(String.format("DeviceManager.requestDeviceList(%s): Deviceroup Not Present", r.groupId));
					}
				}).match(DeviceManager.ReadDeviceGroupTemperature.class, this::onAllTemperature)
				.match(Terminated.class, this::onTerminate).build();
	}
	
	public void onAllTemperature(DeviceManager.ReadDeviceGroupTemperature r) {
		ActorRef requester = getSender();
		if (groupToActors.containsKey(r.groupId)) {
			ActorRef deviceGroupActor = groupToActors.get(r.groupId);
			Future<Object> result = Patterns.ask(deviceGroupActor, new DeviceGroup.RequestAllTemperatures(r.requestId), 100);
			Timeout timeout = new Timeout(100, TimeUnit.MILLISECONDS);
			try {
				DeviceGroup.RespondAllTemperatures reply = (DeviceGroup.RespondAllTemperatures)Await.result(result, timeout.duration());
				requester.tell(new DeviceGroupTemperature(r.requestId, r.groupId, reply.readings), getSelf());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void onRegisterDevice(RequestRegisterDevice r) {
		if (!groupToActors.containsKey(r.groupId)) {
			ActorRef deviceGroup = getContext().actorOf(DeviceGroup.props(r.groupId));
			actorsToGroups.put(deviceGroup, r.groupId);
			groupToActors.put(r.groupId, deviceGroup);
			deviceGroup.forward(r, getContext());
			getContext().watch(deviceGroup);
		} else {
			groupToActors.get(r.groupId).forward(r, getContext());
		}
	}

	public void onTerminate(Terminated t) {
		if (actorsToGroups.containsKey(t.actor())) {
			String groupId = actorsToGroups.get(t.actor());
			log.info(String.format("DeviceManager.onTerminateDeviceGroup(%s)", groupId));
			groupToActors.remove(groupId);
			actorsToGroups.remove(t.actor());
			getContext().unwatch(t.actor());
		}
	}
	//Messages for Requesting all devices of a group
	public static class RequestDeviceList {
		public long requestId;
		public String groupId;

		public RequestDeviceList(long requestId, String groupId) {
			this.groupId = groupId;
			this.requestId = requestId;
		}
	}
	public static class DeviceList {
		public long requestId;
		public String groupId;
		public Set<String> deviceIds;

		public DeviceList(long requestId, String groupId, Set<String> deviceIds) {
			this.groupId = groupId;
			this.requestId = requestId;
			this.deviceIds = deviceIds;
		}
		
		public String getResponse() {
			StringBuilder builder = new StringBuilder();
			this.deviceIds.stream().forEach(r -> builder.append(r).append("\n"));
			return builder.toString();
		}
	}

	// Messages for Registering devices
	public static class RequestRegisterDevice {
		public String deviceId;
		public String groupId;

		public RequestRegisterDevice(String groupId, String deviceId) {
			this.groupId = groupId;
			this.deviceId = deviceId;
		}
	}

	public static class DeviceRegistered {
		public String deviceId;
		public String groupId;

		public DeviceRegistered(String groupId, String deviceId) {
			this.groupId = groupId;
			this.deviceId = deviceId;
		}
	}

	// Device's Record Temperature
	public static class RecordTemperature {
		public long requestId;
		public String deviceId;
		public String groupId;
		public Double temperature;

		public RecordTemperature(long requestId, String groupId, String deviceId, Double temp) {
			this.requestId = requestId;
			this.groupId = groupId;
			this.deviceId = deviceId;
			this.temperature = temp;
		}
	}

	// Device's Temperature Reading
	public static class ReadDeviceTemperature {
		public long requestId;
		public String deviceId;
		public String groupId;

		public ReadDeviceTemperature(long requestId, String groupId, String deviceId) {
			this.requestId = requestId;
			this.groupId = groupId;
			this.deviceId = deviceId;
		}
	}

	public static class DeviceTemperature {
		public long requestId;
		public String deviceId;
		public String groupId;
		public double temperature;

		public DeviceTemperature(long requestId, String groupId, String deviceId, double temp) {
			this.requestId = requestId;
			this.groupId = groupId;
			this.deviceId = deviceId;
			this.temperature = temp;
		}
	}
	
	//DeviceGroup's Temperatures
	public static class ReadDeviceGroupTemperature {
		public long requestId;
		public String groupId;

		public ReadDeviceGroupTemperature(long requestId, String groupId) {
			this.requestId = requestId;
			this.groupId = groupId;
		}
	}

	public static class DeviceGroupTemperature {
		public long requestId;
		public String groupId;
		public Map<String, TemperatureReading> readings;

		public DeviceGroupTemperature(long requestId, String groupId, Map<String, DeviceGroup.TemperatureReading> readings) {
			this.requestId = requestId;
			this.groupId = groupId;
			this.readings = readings;
		}
		
		public String getResponse() {
			StringBuilder builder = new StringBuilder();
			readings.keySet().stream().forEach((deviceId) -> builder.append(String.format("%s: %s\n", deviceId, getTemperatureReading(readings.get(deviceId)))));
			return builder.toString();
		}
		
		public String getTemperatureReading(TemperatureReading reading) {
			if (reading instanceof TemperatureAvailable) {
				return String.valueOf(((TemperatureAvailable) reading).temperature);
			} else if (reading instanceof TemperatureNotAvailable) {
				return "TemperatureNotAvailable";
			} else if (reading instanceof TemperatureNotAvailable) {
				return "TemperatureNotAvailable";
			} else if (reading instanceof DeviceTimedout) {
				return "DeviceTimedout";
			} else if (reading instanceof DeviceTerminated) {
				return "DeviceTerminated";
			} else {
				return reading.getClass().getSimpleName();
			}
		}
	}
}
