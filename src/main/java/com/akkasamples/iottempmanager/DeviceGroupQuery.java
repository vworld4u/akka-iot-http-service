package com.akkasamples.iottempmanager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.akkasamples.iottempmanager.DeviceGroup.TemperatureReading;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.FiniteDuration;

public class DeviceGroupQuery extends AbstractActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	private long requestId;
	private ActorRef requester;
	private Map<ActorRef, String> actorsToDevices;
	private Cancellable queryTimer;

	public DeviceGroupQuery(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
		this.requestId = requestId;
		this.requester = requester;
		this.actorsToDevices = actorToDeviceId;

		queryTimer = getContext().getSystem().scheduler().scheduleOnce(timeout, getSelf(), new CollectionTimeout(), getContext().dispatcher(), getSelf());
	}

	public static Props props(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
		return Props.create(DeviceGroupQuery.class, () -> new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout));
	}

	@Override
	public void preStart() throws Exception {
		log.info(String.format("DeviceGroupQuery{%d}.preStart()", requestId));
		for (ActorRef deviceActor : actorsToDevices.keySet()) {
			getContext().watch(deviceActor);
			deviceActor.tell(new Device.ReadTemperature(0L), getSelf());
		}
	}

	@Override
	public void postStop() throws Exception {
		log.info(String.format("DeviceGroupQuery{%d}.postStop()", requestId));
		this.queryTimer.cancel();
	}

	@Override
	public Receive createReceive() {
		return waitingForReplies(new HashMap<>(), actorsToDevices.keySet());
	}
	
	private Receive waitingForReplies(Map<String, DeviceGroup.TemperatureReading> repliesSofar, Set<ActorRef> stillWaiting) {
		return receiveBuilder().match(Device.RespondTemperature.class, r -> {
			ActorRef sender = getSender();
			TemperatureReading reading = r.temperature.map(v -> (DeviceGroup.TemperatureReading) new DeviceGroup.TemperatureAvailable(v)).orElse(new DeviceGroup.TemperatureNotAvailable());
			responseReceived(repliesSofar, stillWaiting, reading, sender);
		})
		.match(Terminated.class, t -> {
			responseReceived(repliesSofar, stillWaiting, new DeviceGroup.DeviceTerminated(), t.actor());
		})
		.match(CollectionTimeout.class, r -> {
			Map<String, TemperatureReading> replies = new HashMap<>(repliesSofar);
			for (ActorRef actor: stillWaiting) {
				replies.put(actorsToDevices.get(actor), new DeviceGroup.DeviceTimedout());
				requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, replies), getSelf());
				getContext().stop(getSelf());
			}
		})
		.build();
	}

	private void responseReceived(Map<String, TemperatureReading> repliesSofar, Set<ActorRef> stillWaiting, TemperatureReading reading, ActorRef actor) {
		getContext().unwatch(actor);
		String deviceId = actorsToDevices.get(actor);
		Set<ActorRef> newPending = new HashSet<>(stillWaiting);
		newPending.remove(actor);
		
		Map<String, DeviceGroup.TemperatureReading> replies = new HashMap<>(repliesSofar);
		replies.put(deviceId, reading);
		if (newPending.isEmpty()) {
			requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, replies), getSelf());
			getContext().stop(getSelf());
		} else {
			getContext().become(waitingForReplies(replies, newPending));
		}
	}

	public static class CollectionTimeout {
	}

}
