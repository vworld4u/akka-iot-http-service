package com.akkasamples.iottempmanager.main;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import com.akkasamples.iottempmanager.DeviceManager;
import com.akkasamples.iottempmanager.DeviceManager.DeviceList;
import com.akkasamples.iottempmanager.IoTManager;
import com.sun.javafx.tools.packager.Log;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.StringUnmarshallers;
import akka.pattern.Patterns;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

public class IoTHttpApplication extends AllDirectives {
	public static LoggingAdapter log;

	public static void main(String[] args) throws IOException {
		// boot up server using the route as defined below
	    ActorSystem system = ActorSystem.create("iotApplication");
	    ActorRef iotManager = system.actorOf(IoTManager.props());
	    log = Logging.getLogger(system, system);
	    final Http http = Http.get(system);
	    final ActorMaterializer materializer = ActorMaterializer.create(system);
	  //In order to access all directives we need an instance where the routes are define.
	    IoTHttpApplication app = new IoTHttpApplication();
	    final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute(iotManager).flow(system, materializer);
	    final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
	        ConnectHttp.toHost("localhost", 8080), materializer);

	    System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
	    int byteRead;
	    do {
	    	byteRead = System.in.read(); // let it run until user presses return
	    	if (byteRead == ' ') break;
	    } while (byteRead != ' ');

	    binding
	        .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
	        .thenAccept(unbound -> system.terminate()); // and shutdown when done

	}
	
	private Route createRoute(ActorRef iotManager) {
		Random random = new Random();
	    return route(
	        path("device", () -> route(
	        	post(() -> 
		        	parameter(StringUnmarshallers.STRING, "groupId", groupId ->
		            	parameter(StringUnmarshallers.STRING, "deviceId", deviceId -> {
		            		Log.info(String.format("registerNewDevice(GroupId=%s, DeviceId=%s)", groupId, deviceId));
		            		iotManager.tell(new DeviceManager.RequestRegisterDevice(groupId, deviceId), ActorRef.noSender());
		            		return complete("Post-Device-Registration-Response");
		            	})
		        	))
	        )),
	        path("temperature", () -> route(
	        	get(() -> 
	        		parameter(StringUnmarshallers.STRING, "groupId", groupId ->
		            	parameter(StringUnmarshallers.STRING, "deviceId", deviceId -> {
		            		log.info(String.format("requestDeviceTemperature: GroupId = %s DeviceId = %s", groupId, deviceId));
		        			CompletionStage<DeviceManager.DeviceTemperature> list = PatternsCS.ask(iotManager, new DeviceManager.ReadDeviceTemperature(random.nextLong(), groupId, deviceId), 500).thenApply(DeviceManager.DeviceTemperature.class::cast);
		        			return onSuccess(list, (dl) -> complete(dl.deviceId + " " + dl.temperature));
		            	})
	        	)),
	        	get(() -> 
	        		parameter(StringUnmarshallers.STRING, "groupId", groupId -> {
	        			log.info(String.format("requestDeviceGroupTemperature: GroupId = %s ", groupId));
	        			CompletionStage<DeviceManager.DeviceGroupTemperature> list = PatternsCS.ask(iotManager, new DeviceManager.ReadDeviceGroupTemperature(random.nextLong(), groupId), 500).thenApply(DeviceManager.DeviceGroupTemperature.class::cast);
	        			return onSuccess(list, (dl) -> complete(dl.groupId + "\n" + dl.getResponse()));
		            })
        		),
	        	post(() -> 
        			parameter(StringUnmarshallers.STRING, "groupId", groupId ->
        				parameter(StringUnmarshallers.STRING, "deviceId", deviceId ->
        					parameter(StringUnmarshallers.DOUBLE, "temperature", temperature -> {
        						iotManager.tell(new DeviceManager.RecordTemperature(random.nextLong(), groupId, deviceId, temperature), ActorRef.noSender());
        						return complete("Recording completed");
        				}))
        		))
	        )),
	        path("devicegroup", () -> route(
		        	get(() -> 
		        		parameter(StringUnmarshallers.STRING, "groupId", groupId ->{
		        			log.info(String.format("requestDeviceGroupDeviceList: GroupId = %s", groupId));
		        			CompletionStage<DeviceManager.DeviceList> list = PatternsCS.ask(iotManager, new DeviceManager.RequestDeviceList(random.nextLong(), groupId), 500).thenApply(DeviceManager.DeviceList.class::cast);
		        			return onSuccess(list, (dl) -> complete(dl.getResponse()));
			            })
		        	)
		        ))
	        );
	  }

}
