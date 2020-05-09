package com.racing.akked;

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

    private RaceController(ActorContext<RaceController.Command> context) {
        super(context);
    }

    public static Behavior<RaceController.Command> create() {
        return Behaviors.setup(RaceController::new);
    }

    private Map<ActorRef<Racer.Command>, Integer> currentPositions;
    private long start;
    private int raceLength = 100;
    private Object TIMER_KEY;

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()

                .onMessage(StartCommand.class, command ->{
                    start = System.currentTimeMillis();
                    currentPositions = new HashMap<>();
                    for (int i = 0; i < 10; i++) {
                        ActorRef<Racer.Command> racer = getContext().spawn(Racer.create(), "racer" + i);
                        currentPositions.put(racer, 0);
                        racer.tell(new Racer.StartCommand(raceLength));
                    }

                    return Behaviors.withTimers(timer -> {
                        timer.startTimerAtFixedRate(TIMER_KEY, new GetPositionsCommand(), Duration.ofSeconds(1));
                        return this;
                    });
                })

                .onMessage(GetPositionsCommand.class, command -> {
                    currentPositions.keySet().forEach(
                        racer -> racer.tell(new Racer.PositionCommand(getContext().getSelf()))
                    );
                    displayRace();
                    return this;
                })

                .onMessage(RacerUpdateCommand.class, command -> {
                    currentPositions.put(command.getRacer(), command.getPosition());
                    return this;
                })

                .build();
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
