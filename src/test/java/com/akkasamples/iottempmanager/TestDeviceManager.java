package com.akkasamples.iottempmanager;

import static org.junit.Assert.assertEquals;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.akkasamples.iottempmanager.DeviceGroup.TemperatureAvailable;
import com.akkasamples.iottempmanager.DeviceGroup.TemperatureNotAvailable;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

public class TestDeviceManager {

	private static ActorSystem system;
	
	@BeforeClass
	public static void beforeTest() {
		system = ActorSystem.create("test");
	}
	
	@AfterClass
	public static void teardown() {
		TestKit.shutdownActorSystem(system);
        system = null;
	}
	
	@Test
	public void testDeviceRegistration() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device");
		assertEquals(r.groupId, "group");
	}

	@Test
	public void testRequestDeviceList() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device1"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device1");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device2"), probe.getRef());
		r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device2");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.RequestDeviceList(10L, "group"), probe.getRef());
		DeviceManager.DeviceList list = probe.expectMsgClass(DeviceManager.DeviceList.class);
		assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), list.deviceIds);
	}
	
	@Test
	public void testReadInvalidDeviceList() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device1"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device1");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device2"), probe.getRef());
		r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device2");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.RequestDeviceList(10L, "group2"), probe.getRef());
		probe.expectNoMessage();
	}
	
	@Test
	public void testRecordDeviceTemperature() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.RecordTemperature(10L, "group", "device", 10.00), probe.getRef());
		probe.expectNoMessage();
	}
	
	@Test
	public void testRecordInvalidDeviceTemperature() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device1"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device1");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.RecordTemperature(10L, "group2", "device1", 10.00), probe.getRef());
		probe.expectNoMessage();
	}
	
	@Test
	public void testReadInvalidDeviceTemperature() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device1"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device1");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.ReadDeviceTemperature(10L, "group", "device2"), probe.getRef());
		probe.expectNoMessage();
	}

	@Test
	public void testReadValidEmptyDeviceTemperature() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device");
		assertEquals(r.groupId, "group");
		deviceManagerActor.tell(new DeviceManager.ReadDeviceTemperature(10L, "group", "device"), probe.getRef());
		DeviceManager.DeviceTemperature temp = probe.expectMsgClass(DeviceManager.DeviceTemperature.class);
		assertEquals(temp.requestId, 10L);
		assertEquals(Double.valueOf(-1.00), Double.valueOf(temp.temperature));
	}

	@Test
	public void testReadValidDeviceTemperature() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device");
		assertEquals(r.groupId, "group");
		
		deviceManagerActor.tell(new DeviceManager.RecordTemperature(10L, "group", "device", 10.00), probe.getRef());
		probe.expectNoMessage();

		deviceManagerActor.tell(new DeviceManager.ReadDeviceTemperature(10L, "group", "device"), probe.getRef());
		DeviceManager.DeviceTemperature temp = probe.expectMsgClass(DeviceManager.DeviceTemperature.class);
		assertEquals(temp.requestId, 10L);
		assertEquals(Double.valueOf(10.00), Double.valueOf(temp.temperature));
	}
	

	@Test
	public void testReadDeviceGroupTemperatures() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());
		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device1"), probe.getRef());
		DeviceManager.DeviceRegistered r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device1");
		assertEquals(r.groupId, "group");

		deviceManagerActor.tell(new DeviceManager.RequestRegisterDevice("group", "device2"), probe.getRef());
		r = probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(r.deviceId, "device2");
		assertEquals(r.groupId, "group");
		
		deviceManagerActor.tell(new DeviceManager.RecordTemperature(10L, "group", "device1", 10.00), probe.getRef());
		probe.expectNoMessage();

		deviceManagerActor.tell(new DeviceManager.ReadDeviceTemperature(10L, "group", "device1"), probe.getRef());
		DeviceManager.DeviceTemperature temp = probe.expectMsgClass(DeviceManager.DeviceTemperature.class);
		assertEquals(temp.requestId, 10L);
		assertEquals(Double.valueOf(10.00), Double.valueOf(temp.temperature));
		
		deviceManagerActor.tell(new DeviceManager.ReadDeviceGroupTemperature(10L, "group"), probe.getRef());
		DeviceManager.DeviceGroupTemperature t = probe.expectMsgClass(DeviceManager.DeviceGroupTemperature.class);
		assertEquals(TemperatureAvailable.class, t.readings.get("device1").getClass());
		assertEquals(((TemperatureAvailable)t.readings.get("device1")).temperature, Double.valueOf(10.00));
		assertEquals(TemperatureNotAvailable.class, t.readings.get("device2").getClass());
	}
}
