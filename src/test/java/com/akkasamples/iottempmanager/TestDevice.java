package com.akkasamples.iottempmanager;

import static org.junit.Assert.assertEquals;

import java.util.Optional;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

public class TestDevice {
	static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }
 
	@Test
	public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
		TestKit probe = new TestKit(system);
		ActorRef deviceActor = system.actorOf(Device.props("group", "device"));
		deviceActor.tell(new Device.ReadTemperature(42L), probe.getRef());
		Device.RespondTemperature response = probe.expectMsgClass(Device.RespondTemperature.class);
		assertEquals(42L, response.requestId);
		assertEquals(Optional.empty(), response.temperature);
	}
	
	@Test
	public void testRecordTemperature() {
		TestKit probe = new TestKit(system);
		ActorRef deviceActor = system.actorOf(Device.props("group", "device"));
		deviceActor.tell(new Device.RecordTemperature(42L, 1.00), probe.getRef());
		Device.TemperatureRecorded response = probe.expectMsgClass(Device.TemperatureRecorded.class);
		assertEquals(42L, response.requestId);
		assertEquals(Double.valueOf(1.00), response.temperature.orElse(-1.00));
	}

	@Test
	public void testReadValidTemperature() {
		TestKit probe = new TestKit(system);
		ActorRef deviceActor = system.actorOf(Device.props("group", "device"));
		deviceActor.tell(new Device.RecordTemperature(42L, 1.00), probe.getRef());
		Device.TemperatureRecorded response = probe.expectMsgClass(Device.TemperatureRecorded.class);
		assertEquals(42L, response.requestId);
		assertEquals(Double.valueOf(1.00), response.temperature.orElse(-1.00));
		deviceActor.tell(new Device.ReadTemperature(42L), probe.getRef());
		Device.RespondTemperature responseTemp = probe.expectMsgClass(Device.RespondTemperature.class);
		assertEquals(42L, responseTemp.requestId);
		assertEquals(Double.valueOf(1.00), responseTemp.temperature.orElse(-1.00));
	}

}
