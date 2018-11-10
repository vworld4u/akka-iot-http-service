package com.akkasamples.iottempmanager.main;

import com.akkasamples.iottempmanager.IoTManager;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class IoTMain {

	public static void main(String[] args) {
		ActorSystem system = ActorSystem.create("iot-system");
		try {
			ActorRef iotManager = system.actorOf(IoTManager.props(), "iot-supervisor");
			
			System.out.println("Press ENTER to exit the system");
			System.in.read();
	    } catch(Exception e) {
	    	System.err.println(e);
	    	e.printStackTrace(System.err);
	    } finally {
	      system.terminate();
	    }
	}

}
