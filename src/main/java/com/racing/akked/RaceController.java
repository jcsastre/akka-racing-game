package com.racing.akked;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class RaceController extends AbstractBehavior<RaceController.Command> {

    static final Logger logger = LoggerFactory.getLogger(RaceController.class);

    public interface Command extends Serializable {};

    public static class StartCommand implements Command {
        public static final long serialVersionUID = 1L;
    }

    @Value
    public static class RacerUpdateCommand implements Command {
        public static final long serialVersionUID = 1L;
        ActorRef<Racer.Command> racer;
        int position;
    }

    private class GetPositionsCommand implements Command {
        public static final long serialVersionUID = 1L;
    }

    @Value
    public static class RaceFinishedCommand implements Command {
        public static final long serialVersionUID = 1L;
        ActorRef<Racer.Command> racer;
    }

    private RaceController(ActorContext<RaceController.Command> context) {
        super(context);
    }

    public static Behavior<RaceController.Command> create() {
        return Behaviors.setup(RaceController::new);
    }

    private Map<ActorRef<Racer.Command>, Integer> currentPositions;
    private Map<ActorRef<Racer.Command>, Long> finishingTimes;
    private long start;
    private int raceLength = 100;
    private Object TIMER_KEY;

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()

                .onMessage(StartCommand.class, command ->{
                    start = System.currentTimeMillis();
                    currentPositions = new HashMap<>();
                    finishingTimes = new HashMap<>();
                    for (int i = 0; i < 10; i++) {
                        ActorRef<Racer.Command> racer = getContext().spawn(Racer.create(), "racer" + i);
                        currentPositions.put(racer, 0);
                        racer.tell(new Racer.StartCommand(raceLength));
                    }

                    return Behaviors.withTimers(timer -> {
                        timer.startTimerAtFixedRate(TIMER_KEY, new GetPositionsCommand(), Duration.ofSeconds(1));
                        return Behaviors.same();
                    });
                })

                .onMessage(GetPositionsCommand.class, command -> {
                    currentPositions.keySet().forEach(
                        racer -> racer.tell(new Racer.PositionCommand(getContext().getSelf()))
                    );
                    displayRace();
                    return Behaviors.same();
                })

                .onMessage(RacerUpdateCommand.class, command -> {
                    currentPositions.put(command.getRacer(), command.getPosition());
                    return Behaviors.same();
                })

                .onMessage(RaceFinishedCommand.class, command -> {
                    finishingTimes.put(command.getRacer(), System.currentTimeMillis());
                    if (finishingTimes.size() == 10) {
                        return raceCompleteMessageHandler();
                    } else {
                        return Behaviors.same();
                    }
                })

                .build();
    }

    public Receive<Command> raceCompleteMessageHandler() {
        return newReceiveBuilder()

                .onMessage(GetPositionsCommand.class, command -> {
                    displayResults();
                    return Behaviors.withTimers(timers -> {
                        timers.cancel(TIMER_KEY);
                        return Behaviors.stopped();
                    });
                })

                .build();
    }

    private void displayResults() {
        System.out.println("Results");
        finishingTimes.values().stream().sorted().forEach(it -> {
            for (ActorRef<Racer.Command> racer : finishingTimes.keySet()) {
                if (finishingTimes.get(racer) == it) {
                    final String path = racer.path().toString();
                    final String racerId = path.substring(path.length() - 1);
                    logger.info("Racer " + racerId + " finished in " + ( (double)it - start ) / 1000 + " seconds.");
                }
            }
        });
    }

    private void displayRace() {
        int displayLength = 160;
        for (int i = 0; i < 50; ++i) System.out.println();
        System.out.println("Race has been running for " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
        System.out.println("    " + new String (new char[displayLength]).replace('\0', '='));
        int i = 0;
        for (ActorRef<Racer.Command> racer : currentPositions.keySet()) {
            System.out.println(i + " : "  + new String (new char[currentPositions.get(racer) * displayLength / 100]).replace('\0', '*'));
            i++;
        }
    }
}
