package org.learning.javaconcurrency.controller;

import java.util.concurrent.CountDownLatch;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

import org.learning.javaconcurrency.Actors;
import org.learning.javaconcurrency.Event;
import org.learning.javaconcurrency.akka.Master;
import org.learning.javaconcurrency.akka.MasterWithParallelConsumer;
import org.learning.javaconcurrency.disruptor.DisruptorService;
import org.learning.javaconcurrency.disruptor.NonBlockingAsyncDisruptorService;
import org.learning.javaconcurrency.executor.AsyncExecutorService;
import org.learning.javaconcurrency.executor.BasicExecutorService;
import org.learning.javaconcurrency.executor.NonBlockingAsyncExecutorService;
import org.learning.javaconcurrency.reactive.ReactiveService;
import org.learning.javaconcurrency.sequential.SequentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import akka.actor.ActorRef;

/**
 * Created by vkasiviswanathan on 1/2/19.
 */
@Controller
@Path("")
public class ConcurrencyAnalysisController {

	private static final Logger LOG = LoggerFactory.getLogger(ConcurrencyAnalysisController.class);

	static {
		int cores = Runtime.getRuntime().availableProcessors();
		LOG.info("No of. cores in System : " + cores);
		LOG.info("com.sun.management.jmxremote : " + System.getProperty("com.sun.management.jmxremote"));
		LOG.info("com.sun.management.jmxremote.port : " + System.getProperty("com.sun.management.jmxremote.port"));
		LOG.info("com.sun.management.jmxremote.authenticate : "
				+ System.getProperty("com.sun.management.jmxremote.authenticate"));
		LOG.info("com.sun.management.jmxremote.ssl : " + System.getProperty("com.sun.management.jmxremote.ssl"));
		LOG.info("java.rmi.server.hostname : " + System.getProperty("java.rmi.server.hostname"));
	}

	@Autowired
	SequentialService sequentialService;

	@Autowired
	BasicExecutorService basicExecutorService;

	@Autowired
	AsyncExecutorService asyncExecutorService;

	@Autowired
	NonBlockingAsyncExecutorService nonBlockingAsyncExecutorService;

	@Autowired
	ReactiveService reactiveService;

	@Autowired
	DisruptorService disruptorService;

	@Autowired
	NonBlockingAsyncDisruptorService nonBlockingAsyncDisruptorService;

	@GET
	@Path("/sequential-processing")
	public String analyseSequentialProcessing() {
		LOG.info("Analyse Sequential service");
		return sequentialService.getResponse();
	}

	@GET
	@Path("/executor-service")
	public String analyseExecutorService(@DefaultValue("8") @QueryParam("ioPoolSize") int ioPoolSize,
			@DefaultValue("0") @QueryParam("nonIOPoolSize") int nonIOPoolSize,
			@DefaultValue("false") @QueryParam("fixedWorkerThread") boolean fixedWorkerThread) {
		LOG.info("Analyse Executor service");
		LOG.info("ioPool - " + ioPoolSize + " - nonIoPool - " + nonIOPoolSize + " - fixedWorkerThread - "
				+ fixedWorkerThread);
		return basicExecutorService.getResponse(ioPoolSize, nonIOPoolSize, fixedWorkerThread);
	}

	@GET
	@Path("/async-executor-service")
	public String analyseAsyncExecutorServiceCompletable(@DefaultValue("8") @QueryParam("ioPoolSize") int ioPoolSize,
			@DefaultValue("false") @QueryParam("fixedWorkerThread") boolean fixedWorkerThread) {
		LOG.info("Analyse Async Executor service");
		LOG.info("ioPool - " + ioPoolSize + " - fixedWorkerThread - " + fixedWorkerThread);
		return asyncExecutorService.getAsyncResponse(ioPoolSize, fixedWorkerThread);
	}

	@GET
	@Path("/non-blocking-async-executor-service")
	public void analyseNonBlockingAsyncExecutorServiceCompletable(
			@DefaultValue("8") @QueryParam("ioPoolSize") int ioPoolSize, @Suspended AsyncResponse response,
			@DefaultValue("false") @QueryParam("fixedWorkerThread") boolean fixedWorkerThread) {
		LOG.info("Analyse Non-Blocking Async Executor service");
		LOG.info("ioPool - " + ioPoolSize + " - fixedWorkerThread - " + fixedWorkerThread);
		nonBlockingAsyncExecutorService.sendAsyncResponse(ioPoolSize, fixedWorkerThread, response);
	}

	@GET
	@Path("/reactive")
	public void rxJava(@Suspended AsyncResponse asyncResponse) {
		LOG.info("Analyse Reactive service ");
		reactiveService.sendAsyncResponse(asyncResponse);
	}

	@GET
	@Path("/disruptor")
	public String disruptor() {
		LOG.info("Analyse Disruptor service ");
		return disruptorService.getResponse();
	}

	@GET
	@Path("/non-blocking-async-disruptor")
	public void analyseNonBlockingAsyncDisruptorService(@Suspended AsyncResponse response) {
		LOG.info("Analyse Non-Blocking Async Disruptor service");
		nonBlockingAsyncDisruptorService.sendAsyncResponse(response);
	}

	@GET
	@Path("/akka")
	public String analyseAkka() {
		LOG.info("Analyse Akka framework ");
		Event event = new Event();
		CountDownLatch countDownLatch = new CountDownLatch(5);
		event.countDownLatch = countDownLatch;
		Actors.masterActor.tell(new Master.Request("Get Response", event, Actors.workerActor), ActorRef.noSender());
		try {
			event.countDownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		LOG.info("Akka - send response from Thread : " + Thread.currentThread().getName());
		return event.response;
	}

	@GET
	@Path("/akka-async-with-parallel-consumers")
	public void analyseAkkaWithParallelConsumers(@Suspended AsyncResponse asyncHttpResponse) {
		LOG.info("Analyse Akka framework with Parallel Consumers for CPU-Intensive operation");
		Event event = new Event();
		event.asyncHttpResponse = asyncHttpResponse;
		Actors.masterActorWithParallelConsumer.tell(new MasterWithParallelConsumer.Request("Get Response", event,
				Actors.ioOperationWorker, Actors.workerActor1, Actors.workerActor2), ActorRef.noSender());
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Actors.system.terminate();
	}
}
