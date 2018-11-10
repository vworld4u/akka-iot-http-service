package com.akkasamples.iottempmanager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class IoTManager extends AbstractActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	private ActorRef deviceManagerActor;

	public static Props props() {
		return Props.create(IoTManager.class);
	}

	@Override
	public void preStart() throws Exception {
		log.info("IoTManager.preStart()");
		deviceManagerActor = getContext().actorOf(DeviceManager.props(), "device-manager");
	}
	
	@Override
	public void postStop() throws Exception {
		log.info("IoTManager.postStop()");
//		getContext().stop(deviceManagerActor);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(DeviceManager.RequestRegisterDevice.class, r -> {
					deviceManagerActor.forward(r, getContext());
				})
				.match(DeviceGroup.RequestDeviceList.class, r -> {
					deviceManagerActor.forward(r, getContext());
				})
				.match(DeviceManager.RecordTemperature.class, r -> {
					deviceManagerActor.forward(r, getContext());
				})
				.match(DeviceManager.ReadDeviceTemperature.class, r -> {
					deviceManagerActor.forward(r, getContext());
				})
				.match(DeviceGroup.RequestAllTemperatures.class, r -> {
					deviceManagerActor.forward(r, getContext());
				})
				.match(DeviceManager.RequestDeviceList.class, r -> {
					deviceManagerActor.forward(r, getContext());
				})
				.match(DeviceManager.ReadDeviceGroupTemperature.class, r -> {
					deviceManagerActor.forward(r, getContext());
				})
				.build();
	}

}
