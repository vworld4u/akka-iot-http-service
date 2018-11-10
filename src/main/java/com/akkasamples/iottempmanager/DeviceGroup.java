package com.akkasamples.iottempmanager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import scala.concurrent.duration.FiniteDuration;

public class DeviceGroup extends AbstractActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	private String groupId;
	private Map<String, ActorRef> deviceIdToActors = new HashMap<>();
	private Map<ActorRef, String> actorsToDeviceIds = new HashMap<>();
	final long nextCollectionId = 0L;

	public DeviceGroup(String groupId) {
		this.groupId = groupId;
	}

	public static Props props(String groupId) {
		return Props.create(DeviceGroup.class, () -> new DeviceGroup(groupId));
	}

	@Override
	public void preStart() throws Exception {
		log.info(String.format("DeviceGroup{%s}.preStart()", groupId));
		super.preStart();
	}

	@Override
	public void postStop() throws Exception {
		log.info(String.format("DeviceGroup{%s}.postStop()", groupId));
		super.postStop();
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(DeviceManager.RequestRegisterDevice.class, r -> {
			if (r.groupId.equals(this.groupId)) {
				if (!deviceIdToActors.containsKey(r.deviceId)) {
					ActorRef device = getContext().actorOf(Device.props(groupId, r.deviceId));
					actorsToDeviceIds.put(device, r.deviceId);
					deviceIdToActors.put(r.deviceId, device);
					getContext().watch(device);
				}
				getSender().tell(new DeviceManager.DeviceRegistered(groupId, r.deviceId), getSelf());
			} else {
				log.warning(String.format("DeviceGroup{%s}.registerDevice(%s, %s) received a request for another group ", this.groupId, r.groupId, r.deviceId));
			}
		}).match(RequestDeviceList.class, (r) -> {
			log.info(String.format("DeviceGroup{%s}.deviceList(%d)", this.groupId, r.requestId));
			getSender().tell(new RespondDeviceList(r.requestId, deviceIdToActors.keySet()), getSelf());
		}).match(RequestAllTemperatures.class, this::onAllTemperatures)
		.match(DeviceManager.ReadDeviceTemperature.class, r -> {
			if (deviceIdToActors.containsKey(r.deviceId)) {
				ActorRef deviceActor = deviceIdToActors.get(r.deviceId);
				Future<Object> result = Patterns.ask(deviceActor, new Device.ReadTemperature(r.requestId), 100);
				Timeout timeout = new Timeout(100, TimeUnit.MILLISECONDS);
				try {
					Device.RespondTemperature reply = (Device.RespondTemperature)Await.result(result, timeout.duration());
					getSender().tell(new DeviceManager.DeviceTemperature(r.requestId, r.groupId, r.deviceId, reply.temperature.orElse(-1.00)), getSelf());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		})
		.match(DeviceManager.RecordTemperature.class, r -> {
			if (deviceIdToActors.containsKey(r.deviceId)) {
				log.warning(String.format("DeviceGroup{%s}.recordTemperature(%s, %f):", r.groupId, r.deviceId, r.temperature));
				ActorRef deviceActor = deviceIdToActors.get(r.deviceId);
				Future<Object> result = Patterns.ask(deviceActor, new Device.RecordTemperature(r.requestId, r.temperature), 100);
				Timeout timeout = new Timeout(100, TimeUnit.MILLISECONDS);
				try {
					Device.RespondTemperature reply = (Device.RespondTemperature)Await.result(result, timeout.duration());
					getSender().tell(new DeviceManager.DeviceTemperature(r.requestId, r.groupId, r.deviceId, reply.temperature.orElse(-1.00)), getSelf());
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				log.warning(String.format("DeviceGroup{%s}.recordTemperature(%s, %f): Invalid Device ID specified", r.groupId, r.deviceId, r.temperature));
			}
		})
		.match(Terminated.class, this::onTerminated).build();
	}

	public void onTerminated(Terminated t) {
		ActorRef device = t.actor();
		log.info(String.format("DeviceGroup{%s}.onTerminated(Device Terminated %s)", groupId, device));
		if (actorsToDeviceIds.containsKey(device)) {
			String deviceId = actorsToDeviceIds.get(device);
			actorsToDeviceIds.remove(device);
			deviceIdToActors.remove(deviceId);
			getContext().unwatch(t.actor());
		}
	}

	private void onAllTemperatures(RequestAllTemperatures r) {
		// since Java collections are mutable, we want to avoid sharing them between
		// actors (since multiple Actors (threads)
		// modifying the same mutable data-structure is not safe), and perform a
		// defensive copy of the mutable map:
		//
		// Feel free to use your favourite immutable data-structures library with Akka
		// in Java applications!
		Map<ActorRef, String> actorToDeviceIdCopy = new HashMap<>(this.actorsToDeviceIds);

		getContext().actorOf(DeviceGroupQuery.props(actorToDeviceIdCopy, r.requestId, getSender(), new FiniteDuration(3, TimeUnit.SECONDS)));
	}

	// Messages to list devices
	public static class RequestDeviceList {
		public long requestId;

		public RequestDeviceList(long reqId) {
			this.requestId = reqId;
		}
	}

	public static class RespondDeviceList {
		public long requestId;
		public Set<String> devices;

		public RespondDeviceList(long reqId, Set<String> devices) {
			this.requestId = reqId;
			this.devices = devices;
		}
	}

	// Messages for Reading temperature from device groups
	public static interface TemperatureReading {
	}

	public static final class TemperatureAvailable implements TemperatureReading {
		public Double temperature;

		public TemperatureAvailable(Double temperature) {
			this.temperature = temperature;
		}
	}

	public static final class DeviceTerminated implements TemperatureReading {
	}

	public static final class DeviceTimedout implements TemperatureReading {
	}

	public static final class TemperatureNotAvailable implements TemperatureReading {
	}

	// Request for all temperatures
	public static class RequestAllTemperatures {
		final long requestId;

		public RequestAllTemperatures(long requestId) {
			this.requestId = requestId;
		}
	}

	public static class RespondAllTemperatures {
		public long requestId;
		public Map<String, TemperatureReading> readings;

		public RespondAllTemperatures(long requestId, Map<String, TemperatureReading> readings) {
			this.requestId = requestId;
			this.readings = readings;
		}
	}
}
