package com.akkasamples.iottempmanager;

import java.util.Optional;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.Option;

public class Device extends AbstractActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	private String groupId;
	private String deviceId;
	private Optional<Double> currentTemperature = Optional.empty();

	private Device(String groupId, String deviceId) {
		this.groupId = groupId;
		this.deviceId = deviceId;
	}

	public static Props props(String groupId, String deviceId) {
		return Props.create(Device.class, () -> new Device(groupId, deviceId));
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ReadTemperature.class, r -> {
					log.info(String.format("device{id=%s, gid=%s}.readTemperature(%d): Responding temperature: %f ", deviceId, groupId, r.requestId, currentTemperature.orElse(-1.00)));
					getSender().tell(new RespondTemperature(r.requestId, currentTemperature), getSelf());
				})
				.match(RecordTemperature.class, r -> {
					log.info(String.format("device{id=%s, gid=%s}.recordTemperature(%d, %f): Prev Temperature: %f ", deviceId, groupId, r.requestId, r.temperature, currentTemperature.orElse(-1.00)));
					currentTemperature = Optional.of(r.temperature);
					getSender().tell(new TemperatureRecorded(r.requestId, currentTemperature), getSelf());
				})
				.build();
	}
	
	//Lifecycle methods
	@Override
	public void postStop() throws Exception {
		log.info(String.format("device{id=%s, gid=%s}.postStop()", deviceId, groupId));
	}

	@Override
	public void preStart() throws Exception {
		log.info(String.format("device{id=%s, gid=%s}.preStart(): Current Temperature: %f", deviceId, groupId, currentTemperature.orElse(-1.00)));
	}
	
	@Override
	public void preRestart(Throwable reason, Option<Object> message) throws Exception {
		log.info(String.format("device{id=%s, gid=%s}.preRestart(): Current Temperature: %f", deviceId, groupId, currentTemperature.orElse(-1.00)));
		log.error(String.format("device{id=%s, gid=%s}.preRestart(): Message: %s, Reason = ", deviceId, groupId, message), reason);
	}
	
	@Override
	public void postRestart(Throwable reason) throws Exception {
		super.postRestart(reason);
		log.info(String.format("device{id=%s, gid=%s}.postRestart(): Current Temperature: %f", deviceId, groupId, currentTemperature.orElse(-1.00)));
		log.error(String.format("device{id=%s, gid=%s}.postRestart(): Reason = ", deviceId, groupId), reason);
	}

	// Messages for Read Temperature
	public static class ReadTemperature {
		public long requestId;
		
		public ReadTemperature(long reqId) {
			this.requestId = reqId;
		}
	}
	
	public static class RespondTemperature {
		public long requestId;
		public Optional<Double> temperature;
		
		public RespondTemperature(long reqId, Optional<Double> temperature) {
			this.requestId = reqId;
			this.temperature = temperature;
		}
	}
	
	// Messages for Recording temperature
	public static class RecordTemperature {
		public long requestId;
		public Double temperature;
		
		public RecordTemperature(long reqId, Double temperature) {
			this.requestId = reqId;
			this.temperature = temperature;
		}
	}

	public static class TemperatureRecorded extends RespondTemperature {

		public TemperatureRecorded(long reqId, Optional<Double> temperature) {
			super(reqId, temperature);
		}
	}
}
